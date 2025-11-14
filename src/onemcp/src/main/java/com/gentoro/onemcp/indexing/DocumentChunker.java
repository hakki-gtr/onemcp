package com.gentoro.onemcp.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for splitting documents into semantic chunks suitable for graph indexing.
 *
 * <p>This chunker splits markdown documents intelligently by:
 *
 * <ul>
 *   <li>Respecting markdown section boundaries (headers)
 *   <li>Maintaining context within chunks
 *   <li>Keeping chunk sizes manageable (target ~500-1000 chars)
 *   <li>Preserving code blocks and examples intact
 * </ul>
 */
public class DocumentChunker {
  private static final org.slf4j.Logger log =
      com.gentoro.onemcp.logging.LoggingService.getLogger(DocumentChunker.class);

  // Target chunk size (chars) - can overlap for context
  private static final int MIN_CHUNK_SIZE = 300;
  private static final int TARGET_CHUNK_SIZE = 800;
  private static final int MAX_CHUNK_SIZE = 1500;

  // Pattern to detect markdown headers
  private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
  
  // Pattern to detect code blocks
  private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```", Pattern.MULTILINE);

  /**
   * Split a document into semantic chunks.
   *
   * @param content the document content to chunk
   * @param sourceUri the source URI of the document
   * @return list of document chunks with metadata
   */
  public List<DocumentChunk> chunkDocument(String content, String sourceUri) {
    log.trace("Chunking document: {}", sourceUri);

    if (content == null || content.trim().isEmpty()) {
      log.warn("Empty document content for: {}", sourceUri);
      return List.of();
    }

    List<DocumentChunk> chunks = new ArrayList<>();

    // First, split by major sections (headers)
    List<Section> sections = extractSections(content);

    if (sections.isEmpty()) {
      // No headers found, chunk by size
      chunks.addAll(chunkBySizeWithOverlap(content, sourceUri, null, 0));
    } else {
      // Process each section
      for (Section section : sections) {
        if (section.content.length() <= MAX_CHUNK_SIZE) {
          // Section fits in one chunk
          chunks.add(
              new DocumentChunk(
                  section.content,
                  sourceUri,
                  chunks.size(),
                  section.startOffset,
                  section.endOffset,
                  section.title));
        } else {
          // Section too large, split it
          chunks.addAll(
              chunkBySizeWithOverlap(
                  section.content, sourceUri, section.title, section.startOffset));
        }
      }
    }

    log.debug("Created {} chunks for document: {}", chunks.size(), sourceUri);
    return chunks;
  }

  /**
   * Extract sections from markdown based on headers.
   *
   * @param content the markdown content
   * @return list of sections with titles and content
   */
  private List<Section> extractSections(String content) {
    List<Section> sections = new ArrayList<>();
    Matcher matcher = HEADER_PATTERN.matcher(content);

    int lastEnd = 0;
    String currentTitle = null;
    int currentStart = 0;

    while (matcher.find()) {
      // If we have a previous section, save it
      if (lastEnd > 0) {
        String sectionContent = content.substring(currentStart, matcher.start()).trim();
        if (!sectionContent.isEmpty()) {
          sections.add(new Section(currentTitle, sectionContent, currentStart, matcher.start()));
        }
      }

      // Start new section
      currentTitle = matcher.group(2).trim();
      currentStart = matcher.start();
      lastEnd = matcher.end();
    }

    // Add the last section
    if (lastEnd > 0) {
      String sectionContent = content.substring(currentStart).trim();
      if (!sectionContent.isEmpty()) {
        sections.add(new Section(currentTitle, sectionContent, currentStart, content.length()));
      }
    }

    return sections;
  }

  /**
   * Chunk content by size with overlapping context.
   *
   * @param content the content to chunk
   * @param sourceUri the source URI
   * @param title the section title (if any)
   * @param baseOffset the base offset in the original document
   * @return list of chunks
   */
  private List<DocumentChunk> chunkBySizeWithOverlap(
      String content, String sourceUri, String title, int baseOffset) {
    List<DocumentChunk> chunks = new ArrayList<>();

    // Protect code blocks from being split
    List<CodeBlock> codeBlocks = extractCodeBlocks(content);

    int start = 0;
    int chunkIndex = 0;

    while (start < content.length()) {
      int end = Math.min(start + TARGET_CHUNK_SIZE, content.length());

      // Check if we're inside a code block
      CodeBlock containingBlock = findContainingCodeBlock(start, end, codeBlocks);
      if (containingBlock != null) {
        // Include the entire code block
        end = containingBlock.end;
      } else if (end < content.length()) {
        // Try to break at a natural boundary (paragraph, sentence)
        end = findNaturalBreak(content, start, end);
      }

      String chunkContent = content.substring(start, end).trim();
      if (!chunkContent.isEmpty()) {
        String chunkTitle = title != null ? title : inferTitle(chunkContent);
        chunks.add(
            new DocumentChunk(
                chunkContent,
                sourceUri,
                chunkIndex++,
                baseOffset + start,
                baseOffset + end,
                chunkTitle));
      }

      // Check if we've reached the end
      if (end >= content.length()) {
        break;
      }

      // Move start with some overlap for context (10% of target size)
      int newStart = end - (TARGET_CHUNK_SIZE / 10);
      
      // Ensure we're making progress - avoid infinite loop
      if (newStart <= start) {
        newStart = end;
      }
      
      start = newStart;
    }

    return chunks;
  }

  /**
   * Extract code blocks from content to protect them from splitting.
   *
   * @param content the content to analyze
   * @return list of code block positions
   */
  private List<CodeBlock> extractCodeBlocks(String content) {
    List<CodeBlock> blocks = new ArrayList<>();
    Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);

    while (matcher.find()) {
      blocks.add(new CodeBlock(matcher.start(), matcher.end()));
    }

    return blocks;
  }

  /**
   * Find if a range overlaps with any code block.
   *
   * @param start start position
   * @param end end position
   * @param codeBlocks list of code blocks
   * @return the containing code block, or null
   */
  private CodeBlock findContainingCodeBlock(int start, int end, List<CodeBlock> codeBlocks) {
    for (CodeBlock block : codeBlocks) {
      if (start < block.end && end > block.start) {
        return block;
      }
    }
    return null;
  }

  /**
   * Find a natural break point for chunking (paragraph or sentence boundary).
   *
   * @param content the content
   * @param start start position
   * @param target target end position
   * @return adjusted end position at natural break
   */
  private int findNaturalBreak(String content, int start, int target) {
    // Look for paragraph break (double newline)
    int paragraphBreak = content.lastIndexOf("\n\n", target);
    if (paragraphBreak > start + MIN_CHUNK_SIZE) {
      return paragraphBreak;
    }

    // Look for sentence break
    int sentenceBreak = Math.max(
        content.lastIndexOf(". ", target),
        Math.max(content.lastIndexOf(".\n", target), content.lastIndexOf("? ", target)));

    if (sentenceBreak > start + MIN_CHUNK_SIZE) {
      return sentenceBreak + 1;
    }

    // Look for line break
    int lineBreak = content.lastIndexOf("\n", target);
    if (lineBreak > start + MIN_CHUNK_SIZE) {
      return lineBreak;
    }

    // No good break found, use target
    return target;
  }

  /**
   * Infer a title from chunk content (use first line or sentence).
   *
   * @param content the chunk content
   * @return inferred title
   */
  private String inferTitle(String content) {
    String firstLine = content.split("\n")[0].trim();
    if (firstLine.length() > 80) {
      firstLine = firstLine.substring(0, 77) + "...";
    }
    return firstLine;
  }

  /** Represents a document section with a title. */
  private static class Section {
    final String title;
    final String content;
    final int startOffset;
    final int endOffset;

    Section(String title, String content, int startOffset, int endOffset) {
      this.title = title;
      this.content = content;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }
  }

  /** Represents a code block position. */
  private static class CodeBlock {
    final int start;
    final int end;

    CodeBlock(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  /** Represents a document chunk with metadata. */
  public static class DocumentChunk {
    private final String content;
    private final String sourceUri;
    private final int chunkIndex;
    private final int startOffset;
    private final int endOffset;
    private final String title;

    public DocumentChunk(
        String content,
        String sourceUri,
        int chunkIndex,
        int startOffset,
        int endOffset,
        String title) {
      this.content = content;
      this.sourceUri = sourceUri;
      this.chunkIndex = chunkIndex;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.title = title;
    }

    public String getContent() {
      return content;
    }

    public String getSourceUri() {
      return sourceUri;
    }

    public int getChunkIndex() {
      return chunkIndex;
    }

    public int getStartOffset() {
      return startOffset;
    }

    public int getEndOffset() {
      return endOffset;
    }

    public String getTitle() {
      return title;
    }
  }
}


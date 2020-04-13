/*
 *  This file is part of the DITA-OT ReadtheDocs Plug-in project.
 *  See the accompanying LICENSE file for applicable licenses.
 */

package fox.jason.readthedocs.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Echo;
import org.apache.tools.ant.taskdefs.Move;
import org.apache.tools.ant.util.FileUtils;

//   This function creates a ditamap from a ReadTheDocs YAML

public class CreateDitamapTask extends Task {
  /**
   * Field file.
   */
  private String file;

  /**
   * Field dir.
   */
  private String dir;

  /**
   * Creates a new <code>CreateDitamapTask</code> instance.
   */
  public CreateDitamapTask() {
    super();
    this.dir = null;
    this.file = null;
  }

  /**
   * Method setDir.
   *
   * @param dir String
   */
  public void setDir(String dir) {
    this.dir = dir;
  }

  /**
   * Method setFile.
   *
   * @param file String
   */
  public void setFile(String file) {
    this.file = file;
  }

  private String trimData(String inputText, String key) {
    String text = inputText
      .replace(key, "")
      .replace("'", "")
      .replace("\"", "");
    if (text.endsWith("\"") || text.endsWith("'")) {
      return text.substring(0, text.length() - 1).trim();
    }
    return text.trim();
  }

  private YamlInfo analyseYAML(String filename) throws IOException {
    String indexFile = FileUtils.readFully(new java.io.FileReader(filename));
    boolean pages = false;
    String title = "";
    List<ChapterInfo> chapters = new ArrayList<>();

    chapters.add(new ChapterInfo("Abstract", new ArrayList<>()));
    int currentChapter = 0;

    for (String inputLine : indexFile.split("\n")) {
      String line = inputLine.trim();

      if (line.startsWith("site_name:")) {
        title = trimData(line, "site_name:");
      } else if (line.startsWith("pages:")) {
        pages = true;
      } else if (pages) {
        if (line.startsWith("-")) {
          if (line.endsWith(":")) {
            chapters.add(
              new ChapterInfo(
                trimData(line.substring(0, line.length() - 1), "-"),
                new ArrayList<>()
              )
            );
            currentChapter++;
          } else {
            chapters
              .get(currentChapter)
              .topics.add(
                new TopicInfo(
                  trimData(line.substring(1, line.indexOf(":")).trim(), ""),
                  trimData(line.substring(line.indexOf(":") + 1).trim(), "")
                )
              );
          }
        }
      } else if (line.contains(":")) {
        pages = false;
      }
    }

    return new YamlInfo(title, chapters);
  }

  private class YamlInfo {
    public String title;
    public List<ChapterInfo> chapters;

    public YamlInfo(String title, List<ChapterInfo> chapters) {
      this.title = title;
      this.chapters = chapters;
    }
  }

  private class ChapterInfo {
    public String title;
    public List<TopicInfo> topics;

    public ChapterInfo(String title, List<TopicInfo> topics) {
      this.title = title;
      this.topics = topics;
    }
  }

  private class TopicInfo {
    public String title;
    public String href;

    public TopicInfo(String title, String href) {
      this.title = title;
      this.href = href;
    }
  }

  private void rewriteAsDitamap(YamlInfo yaml) {
    String abstractHref = yaml.chapters.get(0).topics.get(0).href;

    if ("index.md".equals(abstractHref)) {
      Move move = (Move) getProject().createTask("move");
      move.setFile(new java.io.File(dir + "/index.md"));
      move.setTofile(new java.io.File(dir + "/abstract.md"));
      move.perform();
      abstractHref = "abstract.md";
    }

    String ditamap = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    ditamap += "<!DOCTYPE bookmap\n";
    ditamap += "  PUBLIC \"-//OASIS//DTD DITA BookMap//EN\" \"bookmap.dtd\">\n";
    ditamap += "<bookmap>\n";
    ditamap += "  <title>" + yaml.title + "</title>\n";
    ditamap += "  <frontmatter>\n";
    ditamap +=
      "    <bookabstract format=\"md\" href=\"" + abstractHref + "\"/>\n";
    ditamap += "    <booklists>\n";
    ditamap += "      <toc/>\n";
    ditamap += "    </booklists>\n";
    ditamap += "  </frontmatter>\n";

    for (int i = 1; i < yaml.chapters.size(); i++) {
      ditamap =
        ditamap +
        "  <chapter>\n    <topicmeta>\n     <navtitle>" +
        yaml.chapters.get(i).title +
        "</navtitle>\n   </topicmeta>\n";
      for (int j = 0; j < yaml.chapters.get(i).topics.size(); j++) {
        ditamap +=
          "    <topicref format=\"md\" href=\"" +
          yaml.chapters.get(i).topics.get(j).href +
          "\"/>\n";
      }
      ditamap += "  </chapter>\n";
    }
    ditamap += "</bookmap>\n";

    Echo task = (Echo) getProject().createTask("echo");
    task.setFile(new java.io.File(dir + "/document.ditamap"));
    task.setMessage(ditamap);
    task.perform();
  }

  /**
   * Method execute.
   *
   * @throws BuildException if something goes wrong
   */
  @Override
  public void execute() {
    //	@param  dir -   The directory holding the file
    //	@param  file -  The filename.
    //
    if (this.dir == null) {
      throw new BuildException("You must supply a dir");
    }
    if (this.file == null) {
      throw new BuildException("You must supply a file");
    }

    try {
      rewriteAsDitamap(analyseYAML(this.file));
    } catch (IOException e) {
      throw new BuildException("Unable to read file", e);
    }
  }
}

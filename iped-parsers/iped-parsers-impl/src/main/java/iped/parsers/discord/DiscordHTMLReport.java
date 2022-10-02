package iped.parsers.discord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.sax.XHTMLContentHandler;

import iped.parsers.discord.json.DiscordAttachment;
import iped.parsers.discord.json.DiscordMention;
import iped.parsers.discord.json.DiscordReaction;
import iped.parsers.discord.json.DiscordRoot;
import iped.parsers.util.Messages;
import iped.utils.SimpleHTMLEncoder;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordHTMLReport {

    public void startDocument(PrintWriter out) {

    }

    public byte[] convertToHTML(List<DiscordRoot> drl, XHTMLContentHandler xhtml) throws IOException {

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8));

        SimpleDateFormat dateFormat = new SimpleDateFormat(Messages.getString("DiscordParser.DateFormat0"));

        out.println("<!DOCTYPE html>");
        out.println("<HTML>");
        out.println("<HEAD>");
        out.println(" <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
        out.println("<style>" + "TABLE {  border-collapse: collapse; font-family: Arial, sans-serif; } "
                + ".title tr td {border: none;}" + ".td-timestamp p {font-size: 10px;text-align:center}"
                + ".black {background-color: #383838; color:#E0E0E0;}"
                + ".gray {background-color: #585858; color:#E0E0E0;}"
                + "TH { border: solid; font-weight: bold; text-align: center; background-color:#AAAAAA; foreground-color:#FFFFFF; } "
                + "TR { vertical-align: middle; } " + ".rb { background-color:#E7E7E7; vertical-align: middle; } "
                + ".rr {  background-color:#FFFFFF; vertical-align: middle; } "
                + "TD { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; } "
                + ".e { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 150px; font-family: monospace; } "
                + ".a { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 110px; } "
                + ".b { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 450px; } "
                + ".z { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 160px; } "
                + ".c { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: right; vertical-align: middle; word-wrap: break-word;  width: 110px; } "
                + ".h { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 110px; }"
                + ".check {vertical-align: top; }" + " TD:hover[onclick]{background-color:#F0F0F0; cursor:pointer} "
                + "</style>");
        out.println("</HEAD>");
        out.println("<BODY>");

        out.println("<TABLE>");

        // Used to know when user ID changed
        String userID = "";
        String colorClass = "black";
        for (DiscordRoot dr : drl) {

            if (dr.isPinned()) {
                // TODO rule for when message is pinned
            }

            // TODO check mentions roles
            if (!dr.getAuthor().getId().equals(userID)) {

                userID = dr.getAuthor().getId();
                colorClass = (colorClass.equals("black") ? "gray" : "black");

                out.println("<TR class='" + colorClass + "'>");
                out.println("	<TD colspan='2'>");
                out.println("		<TABLE class='title'>");
                out.println("			<TR>");
                out.println("				<TD>");
                if (dr.getAuthor().getURLAvatar() == null) {
                    out.println("					<img src='https://cdn.discordapp.com/avatars/" + dr.getAuthor().getId()
                            + "/" + dr.getAuthor().getAvatar() + ".png' alt='' width='50' height='50'>");
                } else {
                    out.println("					<img src='data:image/png;base64, " + dr.getAuthor().getURLAvatar()
                            + "' alt='' width='50' height='50'>");
                }

                out.println("				</TD>");
                out.println("				<TD>");
                out.println("					<span title='Channel ID=" + dr.getChannel_id() + ", UserID="
                        + dr.getAuthor().getId() + "'>" + dr.getAuthor().getName() + "<b>" + dr.getAuthor().getFullUsername()
                        + "</b></span>");
                out.println("				</TD>");
                out.println("				<TD>");
                out.println((dr.getTimestamp() == null ? "" : dateFormat.format(dr.getTimestamp())));
                out.println("				</TD>");
                out.println("			</TR>");
                out.println("		</TABLE>");
                out.println("	</TD>");
                out.println("</TR>");

            }

            if (dr.getMessageContent() != null && !dr.getMessageContent().equals("")) {

                out.println("<TR class='" + colorClass + "'>");

                // message sending time
                out.println("	<TD class='td-timestamp'>");
                out.println("<P>" + (dr.getTimestamp() == null ? "" : formatDate(dr.getTimestamp())) + "</P>");
                out.println("<P>" + (dr.getEditedTimestamp() == null ? ""
                        : "(" + Messages.getString("DiscordParser.EditTime")
                                + dateFormat.format(dr.getEditedTimestamp()) + ")")
                        + "</P>");
                out.println("	</TD>");

                // message body
                out.println("	<TD>");

                // used for debug
                // out.println("<TABLE><TR><TD>" + dr.toString() + "</TD></TR></TABLE>");

                // rule for calls
                if (dr.getCall() != null) {
                    out.println("<TABLE>");
                    out.println("	<TR>");
                    out.println("		<TD colspan='2'>Call</TD>");
                    out.println("	</TR>");
                    out.println("	<TR>");
                    out.println("		<TD>" + Messages.getString("DiscordParser.Start") + "</TD>)");
                    out.println("		<TD>" + (dr.getTimestamp() == null ? "" : dateFormat.format(dr.getTimestamp()))
                            + "</TD>");
                    out.println("	</TR>");
                    out.println("	<TR>");
                    out.println("		<TD>" + Messages.getString("DiscordParser.End") + "</TD>");
                    out.println("		<TD>" + (dr.getCall().getEndedTimestamp() == null ? ""
                            : dateFormat.format(dr.getCall().getEndedTimestamp())) + "</TD>");
                    out.println("	</TR>");
                    out.println("	<TR>");
                    out.println("		<TD>" + Messages.getString("DiscordParser.Participants") + "</TD>");
                    out.println("		<TD>" + dr.getCall().getParticipantsNames(drl) + "</TD>");
                    out.println("	</TR>");
                    out.println("</TABLE>");
                }

                // rule for reactions
                if (dr.getReactions() != null) {

                    List<String> reactions = new ArrayList<String>();

                    for (DiscordReaction reaction : dr.getReactions()) {
                        reactions.add(reaction.getEmoji().getName());
                    }

                    out.println("<TABLE>");
                    out.println("	<TR>");
                    out.println("		<TD>" + Messages.getString("DiscordParser.Reactions")
                            + String.join(", ", reactions) + "</TD>");
                    out.println("	</TR>");
                    out.println("</TABLE>");
                }

                // rule for attachments
                if (dr.getAttachments() != null && dr.getAttachments().size() > 0) {

                    out.println("<TABLE>");
                    out.println("	<TR>");
                    out.println("		<TD colspan='2'>" + Messages.getString("DiscordParser.Attachments") + "</TD>");
                    out.println("	</TR>");

                    for (DiscordAttachment att : dr.getAttachments()) {
                        out.println("<TR>");
                        out.println("	<TD>");
                        printCheckbox(out, att.getMediaHash());
                        if (att.getContent_type() != null && (att.getContent_type().equals("video/mp4")
                                || att.getContent_type().equals("video/webm"))) {
                            out.println("	<video controls>");
                            out.println("		<source type=\"" + att.getContent_type() + "\" src='data:"
                                    + att.getContent_type() + ";base64," + att.getUrl() + "' alt='" + att.getFilename()
                                    + "' width='400px' height=''>");
                            out.println("	</video controls>");
                        } else {
                            out.println("		<img onclick\"app.open('hash:" + att.getMediaHash() + "'"
                                    + " src=\"data:" + att.getContent_type() + ";base64," + att.getUrl() + "\" alt=\""
                                    + att.getFilename() + "\" width=\"400px\" height=\"\">");
                        }

                        out.println("		<BR/>");
                        out.println("		<a href='" + att.getUrl() + "'>" + att.getFilename() + "</a>");
                        out.println("		<BR/>");
                        if (!att.getChildPornSets().isEmpty()) {
                            out.print("<p><i>" + Messages.getString("WhatsAppReport.FoundInPedoHashDB") + " "
                                    + format(att.getChildPornSets().toString()) + "</i></p>");
                        }
                        out.println("	</TD>");
                        out.println("</TR>");
                    }
                    out.println("</TABLE>");
                }

                String message = dr.getMessageContent();

                // rule for mentions
                if (dr.getMentions() != null && dr.getMentions().size() > 0)
                    for (DiscordMention dm : dr.getMentions())
                        message = StringUtils.replace(message, "<@" + dm.getId() + ">",
                                "<span title='UserID=" + dm.getId() + "'>" + "<B style='color:#0099FF'>@"
                                        + dm.getFullUsername() + "</B>" + "</span>");

                out.println(message);
                out.println("	</TD>");
                out.println("</TR>");
            }
        }

        out.println("</TABLE>");
        out.println("</BODY>");
        out.println("</HTML>");

        out.flush();
        out.close();

        return bout.toByteArray();
    }

    public String formatDate(Date date) {

        SimpleDateFormat df1 = new SimpleDateFormat("hh:mm:ss");
        SimpleDateFormat df2 = new SimpleDateFormat(Messages.getString("DiscordParser.DateFormat2"));

        return "<P>" + df1.format(date) + "<BR/>(" + df2.format(date) + ")</P>";

    }

    private void printCheckbox(PrintWriter out, String hash) {
        out.println("<input class=\"check\" type=\"checkbox\" onclick=\"app.check('hash:" + hash
                + "', this.checked)\" name=\"" + hash + "\" />");
    }

    private static final String format(String s) {
        if (s == null || s.trim().isEmpty())
            return "-"; //$NON-NLS-1$
        else
            return SimpleHTMLEncoder.htmlEncode(s.trim());

    }

}

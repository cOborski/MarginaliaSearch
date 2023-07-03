package nu.marginalia.converting.processor.logic;

import nu.marginalia.converting.model.GeneratorType;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Extract keywords for the document meta generator tag */
public class DocumentGeneratorExtractor {
    private static final String defaultValue = "unset";

    public DocumentGenerator generatorCleaned(Document doc) {

        var tags = doc.select("meta[name=generator]");

        if (tags.size() == 0) {
            // Some sites have a comment in the head instead of a meta tag
            return fingerprintByComments(doc);
        }
        if (tags.size() > 1) {
            return DocumentGenerator.multiple();
        }
        String generator = tags.attr("content");

        // Remove leading or trailing junk from the generator string, "powered by" etc.
        generator = removePrefixOrSuffix(generator);

        if (generator.isBlank())
            return DocumentGenerator.unset();

        String[] parts = StringUtils.split(generator, " ,:!");
        if (parts.length == 0)
            return DocumentGenerator.unset();

        int slashIdx = parts[0].indexOf('/');
        if (slashIdx >= 0) {
            // mozilla and staroffice has a really weird format
            return DocumentGenerator.of(parts[0].substring(0, slashIdx));
        }

        if (parts.length > 3) {
            return DocumentGenerator.unset(); // if it's still very long after trim(), it's probably a custom hand written message
        }

        switch (parts[0]) {
            case "joomla!":
                return DocumentGenerator.of("joomla");
            case "plone":
            case "claris":
            case "one.com":
            case "wix.com":
            case "wpbakery":
                return DocumentGenerator.of(parts[0]);
            case "adobe":
            case "microsoft":
                if (parts.length > 1) {
                    return DocumentGenerator.of(parts[1]);
                }
                else {
                    return DocumentGenerator.of(parts[0]);
                }
        }

        if (parts.length > 1) {
            return DocumentGenerator.of(parts[0], parts[0] + "_" + truncVersion(parts[1]));
        }
        else {
            return DocumentGenerator.of(parts[0]);
        }
    }

    // Fallback logic when there is no meta tag
    private DocumentGenerator fingerprintByComments(Document doc) {

        for (var comment : doc.getElementsByTag("head").comments()) {
            String data = comment.getData();

            if (data.contains("Generated by javadoc")) {
                return DocumentGenerator.of("javadoc");
            }

            if (data.contains("phpBB")) {
                return DocumentGenerator.of("phpbb");
            }
        }

        for (var scriptTags : doc.head().select("script")) {
            if (scriptTags.html().contains("window.lemmyConfig")) {
                return DocumentGenerator.of("lemmy");
            }
        }

        if (doc.getElementById("flarum-json-payload") != null) {
            return DocumentGenerator.of("flarum");
        }

        if (doc.getElementById("_xfClientLoadTime") != null) {
            return DocumentGenerator.of("xenforo");
        }

        if (!doc.getElementsByClass("ipsApp").isEmpty()) {
            return DocumentGenerator.of("invision");
        }

        return DocumentGenerator.unset();
    }

    private String removePrefixOrSuffix(String generator) {

        generator = generator.toLowerCase().trim();

        // strip common prefixes
        for (String prefix : Arrays.asList("powered by ", "generated by ")) {

            if (generator.startsWith(prefix)) {
                generator = generator.substring(prefix.length());
                break;
            }
        }

        int dashIdx = generator.indexOf('-'); // Some strings have values like 'foobar 2.3 - the free online generator!'
        if (dashIdx >= 0) {
            generator = generator.substring(0, dashIdx);
        }

        if (!StringUtils.isAsciiPrintable(generator))
            return "";

        return generator;
    }

    // Censor exact version strings, being able to search by major version is enough
    // for any non-blackhat purpose; creating a directory with exact version string
    // is a security risk for the site owner.
    private String truncVersion(String part) {
        int periodIdx = part.indexOf('.', part.startsWith("0.") ? 2 : 0);

        if (periodIdx < 0)
            return part;

        return part.substring(0, periodIdx);
    }

    public record DocumentGenerator(GeneratorType type, List<String> keywords) {
        public static DocumentGenerator unset() {
            return new DocumentGenerator(GeneratorType.UNKNOWN, List.of(defaultValue));
        }

        public static DocumentGenerator of(String... parts) {
            if (parts.length == 0)
                return unset();

            List<String> keywords = new ArrayList<>(List.of(parts));

            final GeneratorType type = switch (parts[0]) {
                case "joomla", "wordpress", "drupal", "plone", "postnuke", "divi", "freeway", "unicity",
                     "modx", "sitemagic", "agility", "edlio", "blogger", "slider", "slider_revolution", "gravcms",
                     "typo3", "dotnetnuke", "cms", "coremedia", "dspace"
                     -> GeneratorType.CMS;
                case "wix.com", "one.com", "wpbakery", "claris", "wordpress.com", "hubspot",
                     "visual_composer", "mobirise", "everweb", "rapidweaver", "shorthand",
                     "visual", "nitropack",
                    /* these are not SAAS but close enough */
                    "redux", "bootply"
                     -> GeneratorType.SAAS;
                case "staroffice", "word", "frontpage", "dreamweaver", "mshtml",
                     "iweb", "excel", "wordperfect", "netscape", "corel", "powerpoint",
                     "openoffice.org", "openoffice", "latex2html", "lotus", "homesite",
                     "trellix", "yahoo", "libreoffice", "opera", "stone's_webwriter",
                     "pdf2htmlex", "nvu", "mozilla", "golive", "tenfingers", "publisher",
                     "allaire", "neooffice"
                     -> GeneratorType.BOOMER_STATIC;
                case "hugo", "jekyll", "hakyll", "gatsby", "react", "gridsome"
                     -> GeneratorType.ZOOMER_STATIC;
                case "vi", "vim", "emacs", "orgmode", "hand", "vscode", "atom", "bbedit", "nano",
                     "notepad.exe", "gedit", "me",
                     "geany", "sublime", "notepad++", "author",
                     "notepad", "namo", "arachnophilia", "scite",
                     "alleycode", "htmlkit", "acehtml", "bluefish", "htmled", "cutehtml", "fileedit", "cocoa"
                     -> GeneratorType.MANUAL;
                case "vbulletin", "phpbb", "mybb", "nodebb", "flarum", "tribe",
                     "discourse", "lemmy", "xenforo", "invision"
                     -> GeneratorType.FORUM;
                case "mediawiki", "dokuwiki", "sharepoint"
                     -> GeneratorType.WIKI;
                case "pandoc", "mkdocs", "doxygen", "javadoc"
                     -> GeneratorType.DOCS;
                case "woocommerce", "shopfactory", "prestashop", "magento", "shopify", "sitedirect", "seomatic"
                     -> GeneratorType.ECOMMERCE_AND_SPAM;
                default
                     -> GeneratorType.UNKNOWN;
            };

            if (type != GeneratorType.UNKNOWN) {
                keywords.add(type.name().toLowerCase());
            }

            return new DocumentGenerator(type, keywords);
        }

        public static DocumentGenerator multiple() {
            // It's *generally* WordPress or the like that injects multiple generator tags

            return new DocumentGenerator(GeneratorType.CMS, List.of(defaultValue));
        }
    }


}

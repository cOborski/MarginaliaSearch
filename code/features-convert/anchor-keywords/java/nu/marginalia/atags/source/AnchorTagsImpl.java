package nu.marginalia.atags.source;

import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.model.LinkWithText;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AnchorTagsImpl implements AnchorTagsSource {
    private final Connection duckdbConnection;
    private static final Logger logger = LoggerFactory.getLogger(AnchorTagsImpl.class);
    public AnchorTagsImpl(Path atagsPath,
                          List<EdgeDomain> relevantDomains)
            throws SQLException
    {
        duckdbConnection = DriverManager.getConnection("jdbc:duckdb:");

        logger.info("Loading atags from " + atagsPath);

        if (!Files.exists(atagsPath)) {
            throw new IllegalArgumentException("atags file does not exist: " + atagsPath);
        }

        try (var stmt = duckdbConnection.createStatement()) {
            // Insert the domains into a temporary table, then use that to filter the atags table

            stmt.executeUpdate("create table domains (domain varchar)");
            try (var ps = duckdbConnection.prepareStatement("insert into domains values (?)")) {
                for (var domain : relevantDomains) {
                    ps.setString(1, domain.toString());
                    ps.executeUpdate();
                }
            }

            // This is a SQL injection vulnerability if you're a validation tool, but the string comes from a trusted source
            // -- we validate nonetheless to present a better error message
            String path = atagsPath.toAbsolutePath().toString();
            if (path.contains("'")) {
                throw new IllegalArgumentException("atags file path contains a single quote: " + path + " and would break the query.");
            }

            stmt.executeUpdate("""
                create table atags as 
                    select * from '%s'  
                    where dest in (select * from domains)
                """.formatted(path));

            // Free up the memory used by the domains table
            stmt.executeUpdate("drop table domains");

            // Create an index on the dest column to speed up queries
            stmt.executeUpdate("create index atags_dest on atags(dest)");

            // This is probably not necessary
            if (!duckdbConnection.getAutoCommit()) {
                duckdbConnection.commit();
            }
        }

        logger.info("Finished loading!");

    }

    @Override
    public synchronized DomainLinks getAnchorTags(EdgeDomain domain) {
        List<LinkWithText> links = new ArrayList<>();

        try (var ps = duckdbConnection.prepareStatement("""
            select 
                unnest(text) as 'text', 
                unnest(url) as 'url', 
                unnest(source) as 'source'
            from atags
            where dest = ?
            """))
        {
            ps.setString(1, domain.toString());
            var rs = ps.executeQuery();
            while (rs.next()) {
                links.add(new LinkWithText(rs.getString("url"), rs.getString("text"), rs.getString("source")));
            }
            return new DomainLinks(links);
        }
        catch (SQLException ex) {
            logger.warn("Failed to get atags for " + domain, ex);
        }

        return new DomainLinks();
    }

    @Override
    public void close() throws Exception {
        duckdbConnection.close();
    }
}

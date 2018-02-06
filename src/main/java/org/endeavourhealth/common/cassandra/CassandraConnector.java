package org.endeavourhealth.common.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.MappingManager;
import org.endeavourhealth.common.cache.ObjectMapperPool;
import org.endeavourhealth.common.cassandra.ehr.EnumRegistry;
import org.endeavourhealth.common.cassandra.models.Cassandra;
import org.endeavourhealth.common.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraConnector {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraConnector.class);
    private static CassandraConnector instance = null;

    private final Cluster cluster;
    private final Session session;
    private final PreparedStatementCache statementCache;
    private final MappingManager mappingManager;

    private CassandraConnector() {
        String configJson = ConfigManager.getConfiguration("cassandra");
        try {
            Cassandra config = ObjectMapperPool.getInstance().readValue(configJson, Cassandra.class);

        Cluster.Builder builder = Cluster.builder()
                .withCredentials(config.getUsername(), config.getPassword());

        for (String node: config.getNode()) {
            builder = builder.addContactPoint(node);
        }

        //turn on Quorum consistency for everything by default
        builder.withQueryOptions(new QueryOptions().setConsistencyLevel(ConsistencyLevel.QUORUM));

        cluster = builder.build();
        session = cluster.connect();
        statementCache =  new PreparedStatementCache(session);
        mappingManager = new MappingManager(session);

        registerCustomCodecs();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load cassandra config", e);
        }
    }

    public Session getSession() {
        return session;
    }

    public PreparedStatementCache getStatementCache() {
        return statementCache;
    }

    public MappingManager getMappingManager() {
        return mappingManager;
    }

    private void registerCustomCodecs() {
        EnumRegistry.register();
    }

    public static CassandraConnector getInstance() {
        if (instance == null) {
            LOG.trace("Creating cassandra connector");
            instance = new CassandraConnector();
        }

        return instance;
    }

    public static void close() {
        CassandraConnector temp = instance;
        if (instance != null) {

            instance = null;
            LOG.trace("Clearing statement cache...");
            temp.statementCache.clear();

            LOG.trace("Closing sessions...");
            temp.session.close();

            LOG.trace("Closing cluster...");
            temp.cluster.close();

            LOG.trace("Done");
        }
    }
}

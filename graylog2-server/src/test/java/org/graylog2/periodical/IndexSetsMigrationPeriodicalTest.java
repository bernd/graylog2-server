/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.periodical;

import org.graylog2.configuration.ElasticsearchConfiguration;
import org.graylog2.indexer.IndexSet;
import org.graylog2.indexer.indexset.IndexSetConfig;
import org.graylog2.indexer.indexset.IndexSetService;
import org.graylog2.indexer.management.IndexManagementConfig;
import org.graylog2.plugin.cluster.ClusterConfigService;
import org.graylog2.plugin.indexer.retention.RetentionStrategy;
import org.graylog2.plugin.indexer.retention.RetentionStrategyConfig;
import org.graylog2.plugin.indexer.rotation.RotationStrategy;
import org.graylog2.plugin.indexer.rotation.RotationStrategyConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IndexSetsMigrationPeriodicalTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Mock
    private IndexSetService indexSetService;
    @Mock
    private ClusterConfigService clusterConfigService;

    private final ElasticsearchConfiguration elasticsearchConfiguration = new ElasticsearchConfiguration();
    private RotationStrategy rotationStrategy = new StubRotationStrategy();
    private RetentionStrategy retentionStrategy = new StubRetentionStrategy();
    private IndexSetsMigrationPeriodical periodical;


    @Before
    public void setUpService() throws Exception {
        periodical = new IndexSetsMigrationPeriodical(
                elasticsearchConfiguration,
                Collections.singletonMap("test", () -> rotationStrategy),
                Collections.singletonMap("test", () -> retentionStrategy),
                indexSetService,
                clusterConfigService);
    }

    @Test
    public void doRunCreatesDefaultIndexSet() throws Exception {
        final StubRotationStrategyConfig rotationStrategyConfig = new StubRotationStrategyConfig();
        final StubRetentionStrategyConfig retentionStrategyConfig = new StubRetentionStrategyConfig();
        final IndexSetConfig savedIndexSetConfig = IndexSetConfig.builder()
                .id("id")
                .title("title")
                .indexPrefix("prefix")
                .shards(1)
                .replicas(0)
                .rotationStrategy(rotationStrategyConfig)
                .retentionStrategy(retentionStrategyConfig)
                .creationDate(ZonedDateTime.of(2016, 10, 12, 0, 0, 0, 0, ZoneOffset.UTC))
                .build();
        when(clusterConfigService.get(IndexManagementConfig.class)).thenReturn(IndexManagementConfig.create("test", "test"));
        when(clusterConfigService.get(StubRotationStrategyConfig.class)).thenReturn(rotationStrategyConfig);
        when(clusterConfigService.get(StubRetentionStrategyConfig.class)).thenReturn(retentionStrategyConfig);
        when(indexSetService.save(any(IndexSetConfig.class))).thenReturn(savedIndexSetConfig);

        final ArgumentCaptor<IndexSetConfig> indexSetConfigCaptor = ArgumentCaptor.forClass(IndexSetConfig.class);

        periodical.doRun();

        verify(indexSetService).save(indexSetConfigCaptor.capture());
        verify(clusterConfigService).write(IndexSetsMigrationPeriodical.IndexSetMigrated.create());

        final IndexSetConfig capturedIndexSetConfig = indexSetConfigCaptor.getValue();
        assertThat(capturedIndexSetConfig.id()).isNull();
        assertThat(capturedIndexSetConfig.title()).isEqualTo("Default index set");
        assertThat(capturedIndexSetConfig.description()).isEqualTo("The Graylog default index set");
        assertThat(capturedIndexSetConfig.indexPrefix()).isEqualTo(elasticsearchConfiguration.getIndexPrefix());
        assertThat(capturedIndexSetConfig.shards()).isEqualTo(elasticsearchConfiguration.getShards());
        assertThat(capturedIndexSetConfig.replicas()).isEqualTo(elasticsearchConfiguration.getReplicas());
        assertThat(capturedIndexSetConfig.rotationStrategy()).isInstanceOf(StubRotationStrategyConfig.class);
        assertThat(capturedIndexSetConfig.retentionStrategy()).isInstanceOf(StubRetentionStrategyConfig.class);
    }

    @Test
    public void doRunCreatesDefaultIndexSetWithDefaultRotationAndRetentionStrategyConfig() throws Exception {
        final StubRotationStrategyConfig rotationStrategyConfig = new StubRotationStrategyConfig();
        final StubRetentionStrategyConfig retentionStrategyConfig = new StubRetentionStrategyConfig();
        final IndexSetConfig savedIndexSetConfig = IndexSetConfig.builder()
                .id("id")
                .title("title")
                .indexPrefix("prefix")
                .shards(1)
                .replicas(0)
                .rotationStrategy(rotationStrategyConfig)
                .retentionStrategy(retentionStrategyConfig)
                .creationDate(ZonedDateTime.of(2016, 10, 12, 0, 0, 0, 0, ZoneOffset.UTC))
                .build();
        when(clusterConfigService.get(IndexManagementConfig.class)).thenReturn(IndexManagementConfig.create("test", "test"));
        when(clusterConfigService.get(StubRotationStrategyConfig.class)).thenReturn(null);
        when(clusterConfigService.get(StubRetentionStrategyConfig.class)).thenReturn(null);
        when(indexSetService.save(any(IndexSetConfig.class))).thenReturn(savedIndexSetConfig);

        final ArgumentCaptor<IndexSetConfig> indexSetConfigCaptor = ArgumentCaptor.forClass(IndexSetConfig.class);

        periodical.doRun();

        verify(indexSetService).save(indexSetConfigCaptor.capture());
        verify(clusterConfigService).write(IndexSetsMigrationPeriodical.IndexSetMigrated.create());

        final IndexSetConfig capturedIndexSetConfig = indexSetConfigCaptor.getValue();
        assertThat(capturedIndexSetConfig.rotationStrategy()).isInstanceOf(StubRotationStrategyConfig.class);
        assertThat(capturedIndexSetConfig.retentionStrategy()).isInstanceOf(StubRetentionStrategyConfig.class);
    }

    @Test
    public void doRunThrowsIllegalStateExceptionIfIndexManagementConfigIsMissing() throws Exception {
        when(clusterConfigService.get(IndexManagementConfig.class)).thenReturn(null);

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Couldn't find index management configuration");

        periodical.doRun();
    }

    @Test
    public void doRunThrowsIllegalStateExceptionIfRotationStrategyIsMissing() throws Exception {
        when(clusterConfigService.get(IndexManagementConfig.class)).thenReturn(IndexManagementConfig.create("foobar", "test"));

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Couldn't retrieve rotation strategy provider for <foobar>");

        periodical.doRun();
    }

    @Test
    public void doRunThrowsIllegalStateExceptionIfRetentionStrategyIsMissing() throws Exception {
        when(clusterConfigService.get(IndexManagementConfig.class)).thenReturn(IndexManagementConfig.create("test", "foobar"));

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Couldn't retrieve retention strategy provider for <foobar>");

        periodical.doRun();
    }


    @Test
    public void runsForeverReturnsTrue() throws Exception {
        assertThat(periodical.runsForever()).isTrue();
    }

    @Test
    public void stopOnGracefulShutdownReturnsFalse() throws Exception {
        assertThat(periodical.stopOnGracefulShutdown()).isFalse();
    }

    @Test
    public void masterOnlyReturnsTrue() throws Exception {
        assertThat(periodical.masterOnly()).isTrue();
    }

    @Test
    public void startOnThisNodeReturnsFalseIfMigrationWasSuccessfulBefore() throws Exception {
        when(clusterConfigService.get(IndexSetsMigrationPeriodical.IndexSetMigrated.class))
                .thenReturn(IndexSetsMigrationPeriodical.IndexSetMigrated.create());
        assertThat(periodical.startOnThisNode()).isFalse();
    }

    @Test
    public void isDaemonReturnsFalse() throws Exception {
        assertThat(periodical.isDaemon()).isFalse();
    }

    @Test
    public void getInitialDelaySecondsReturns0() throws Exception {
        assertThat(periodical.getInitialDelaySeconds()).isEqualTo(0);
    }

    @Test
    public void getPeriodSecondsReturns0() throws Exception {
        assertThat(periodical.getPeriodSeconds()).isEqualTo(0);
    }

    @Test
    public void getLoggerReturnsClassLogger() throws Exception {
        assertThat(periodical.getLogger().getName()).isEqualTo(IndexSetsMigrationPeriodical.class.getCanonicalName());
    }

    private static class StubRotationStrategy implements RotationStrategy {
        @Override
        public void rotate(IndexSet indexSet) {
        }

        @Override
        public Class<? extends RotationStrategyConfig> configurationClass() {
            return StubRotationStrategyConfig.class;
        }

        @Override
        public RotationStrategyConfig defaultConfiguration() {
            return new StubRotationStrategyConfig();
        }
    }

    private static class StubRotationStrategyConfig implements RotationStrategyConfig {
        @Override
        public String type() {
            return StubRotationStrategy.class.getCanonicalName();
        }
    }

    private static class StubRetentionStrategy implements RetentionStrategy {
        @Override
        public void retain(IndexSet indexSet) {
        }

        @Override
        public Class<? extends RetentionStrategyConfig> configurationClass() {
            return StubRetentionStrategyConfig.class;
        }

        @Override
        public RetentionStrategyConfig defaultConfiguration() {
            return new StubRetentionStrategyConfig();
        }
    }

    private static class StubRetentionStrategyConfig implements RetentionStrategyConfig {
        @Override
        public String type() {
            return StubRetentionStrategy.class.getCanonicalName();
        }
    }
}

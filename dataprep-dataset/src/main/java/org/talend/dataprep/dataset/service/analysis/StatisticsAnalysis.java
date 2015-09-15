package org.talend.dataprep.dataset.service.analysis;

import java.util.List;
import java.util.stream.Stream;

import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.talend.dataprep.api.dataset.ColumnMetadata;
import org.talend.dataprep.api.dataset.DataSetMetadata;
import org.talend.dataprep.api.dataset.DataSetRow;
import org.talend.dataprep.api.dataset.statistics.Statistics;
import org.talend.dataprep.api.dataset.statistics.StatisticsUtils;
import org.talend.dataprep.api.type.Type;
import org.talend.dataprep.api.type.TypeUtils;
import org.talend.dataprep.dataset.exception.DataSetErrorCodes;
import org.talend.dataprep.dataset.service.Destinations;
import org.talend.dataprep.dataset.store.content.ContentStoreRouter;
import org.talend.dataprep.dataset.store.metadata.DataSetMetadataRepository;
import org.talend.dataprep.exception.TDPException;
import org.talend.dataprep.lock.DistributedLock;
import org.talend.dataquality.statistics.cardinality.CardinalityAnalyzer;
import org.talend.dataquality.statistics.frequency.DataFrequencyAnalyzer;
import org.talend.dataquality.statistics.frequency.PatternFrequencyAnalyzer;
import org.talend.dataquality.statistics.numeric.histogram.HistogramAnalyzer;
import org.talend.dataquality.statistics.numeric.histogram.HistogramColumnParameter;
import org.talend.dataquality.statistics.numeric.histogram.HistogramParameter;
import org.talend.dataquality.statistics.numeric.quantile.QuantileAnalyzer;
import org.talend.dataquality.statistics.numeric.summary.SummaryAnalyzer;
import org.talend.dataquality.statistics.quality.ValueQualityAnalyzer;
import org.talend.dataquality.statistics.text.TextLengthAnalyzer;
import org.talend.datascience.common.inference.Analyzer;
import org.talend.datascience.common.inference.Analyzers;
import org.talend.datascience.common.inference.type.DataType;

@Component
public class StatisticsAnalysis implements AsynchronousDataSetAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsAnalysis.class);

    @Autowired
    DataSetMetadataRepository repository;

    @Autowired
    ContentStoreRouter store;

    @JmsListener(destination = Destinations.STATISTICS_ANALYSIS)
    public void analyzeQuality(Message message) {
        try {
            String dataSetId = message.getStringProperty("dataset.id"); //$NON-NLS-1$
            try {
                analyze(dataSetId);
            } finally {
                message.acknowledge();
            }
        } catch (JMSException e) {
            throw new TDPException(DataSetErrorCodes.UNEXPECTED_JMS_EXCEPTION, e);
        }
    }

    @Override
    public void analyze(String dataSetId) {
        if (StringUtils.isEmpty(dataSetId)) {
            throw new IllegalArgumentException("Data set id cannot be null or empty.");
        }
        DistributedLock datasetLock = repository.createDatasetMetadataLock(dataSetId);
        datasetLock.lock();
        try {
            DataSetMetadata metadata = repository.get(dataSetId);
            try (Stream<DataSetRow> stream = store.stream(metadata)) {
                if (metadata != null) {
                    if (!metadata.getLifecycle().schemaAnalyzed()) {
                        LOGGER.debug("Schema information must be computed before quality analysis can be performed, ignoring message");
                        return; // no acknowledge to allow re-poll.
                    }
                    computeStatistics(metadata, stream);
                    // Tag data set quality: now analyzed
                    metadata.getLifecycle().qualityAnalyzed(true);
                    repository.add(metadata);
                } else {
                    LOGGER.info("Unable to analyze quality of data set #{}: seems to be removed.", dataSetId);
                }
            } catch (Exception e) {
                LOGGER.warn("dataset {} generates an error", dataSetId, e);
                throw new TDPException(DataSetErrorCodes.UNABLE_TO_ANALYZE_DATASET_QUALITY, e);
            }
        } finally {
            datasetLock.unlock();
        }

    }

    /**
     * Compute the statistics for the given dataset metadata and content.
     *
     * @param metadata the metadata to compute the statistics for.
     * @param stream the content to compute the statistics from.
     */
    public void computeStatistics(DataSetMetadata metadata, Stream<DataSetRow> stream) {
        // Create a content with the expected format for the StatisticsClientJson class
        final List<ColumnMetadata> columns = metadata.getRow().getColumns();
        DataType.Type[] types = TypeUtils.convert(columns);
        // Set min and max for each column in histogram
        final HistogramParameter histogramParameter = new HistogramParameter();
        for (int i = 0; i < columns.size(); i++) {
            ColumnMetadata column = columns.get(i);
            final boolean isNumeric = Type.NUMERIC.isAssignableFrom(Type.get(column.getType()));
            if (isNumeric) {
                final HistogramColumnParameter columnParameter = new HistogramColumnParameter();
                final Statistics statistics = column.getStatistics();
                columnParameter.setParameters(statistics.getMin(), statistics.getMax(), 8);
                histogramParameter.putColumnParameter(i, columnParameter);
            }
        }
        final HistogramAnalyzer histogramAnalyzer = new HistogramAnalyzer(types, histogramParameter);
        // Select all analysis
        Analyzer[] allAnalyzers = new Analyzer[] {
                new ValueQualityAnalyzer(types),
                // Cardinality (distinct + duplicate)
                new CardinalityAnalyzer(),
                // Frequency analysis (Pattern + data)
                new DataFrequencyAnalyzer(),
                new PatternFrequencyAnalyzer(),
                // Quantile analysis
                new QuantileAnalyzer(types),
                // Summary (min, max, mean, variance)
                new SummaryAnalyzer(types),
                // Histogram
                histogramAnalyzer,
                // Text length analysis (for applicable columns)
                new TextLengthAnalyzer()
        };
        final Analyzer<Analyzers.Result> analyzer = Analyzers.with(allAnalyzers);
        stream.map(row -> row.toArray(DataSetRow.SKIP_TDP_ID)).forEach(analyzer::analyze);
        analyzer.end();
        // Store results back in data set
        StatisticsUtils.setStatistics(columns, analyzer.getResult());
    }

    /**
     * @see AsynchronousDataSetAnalyzer#destination()
     */
    @Override
    public String destination() {
        return Destinations.STATISTICS_ANALYSIS;
    }
}

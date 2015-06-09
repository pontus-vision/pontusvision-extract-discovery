package org.talend.dataprep.transformation.api.action;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.talend.dataprep.api.dataset.DataSetRow;
import org.talend.dataprep.api.dataset.RowMetadata;
import org.talend.dataprep.transformation.api.action.context.TransformationContext;

/**
 * JavaBean that holds all the transformers.
 */
public class ParsedActions {

    /** The row transformers united into a single consumer. */
    private BiConsumer<DataSetRow, TransformationContext> rowTransformer;

    /** The list of metadata transformers. */
    private Consumer<RowMetadata> metadataTransformers;

    /**
     * Default constructor.
     *
     * @param rowTransformer The row transformers united into a single consumer.
     * @param metadataTransformers The list of metadata transformers.
     */
    public ParsedActions(BiConsumer<DataSetRow, TransformationContext> rowTransformer, Consumer<RowMetadata> metadataTransformers) {
        this.rowTransformer = rowTransformer;
        this.metadataTransformers = metadataTransformers;
    }

    /**
     * @return The row transformers united into a single consumer.
     */
    public BiConsumer<DataSetRow, TransformationContext> getRowTransformer() {
        return rowTransformer;
    }

    /**
     * @return The list of metadata transformers.
     */
    public Consumer<RowMetadata> getMetadataTransformer() {
        return metadataTransformers;
    }
}

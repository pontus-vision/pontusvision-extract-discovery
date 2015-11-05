package org.talend.dataprep.transformation.api.action.metadata.datablending;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.talend.dataprep.transformation.api.action.metadata.common.ImplicitParameters.COLUMN_ID;
import static org.talend.dataprep.transformation.api.action.metadata.datablending.Lookup.Parameters.LOOKUP_DS_URL;
import static org.talend.dataprep.transformation.api.action.metadata.datablending.Lookup.Parameters.LOOKUP_JOIN_ON;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.talend.dataprep.api.dataset.ColumnMetadata;
import org.talend.dataprep.api.dataset.DataSetMetadata;
import org.talend.dataprep.api.dataset.DataSetRow;
import org.talend.dataprep.transformation.api.action.context.TransformationContext;
import org.talend.dataprep.transformation.api.action.metadata.ActionMetadataTestUtils;
import org.talend.dataprep.transformation.api.action.parameters.Parameter;

/**
 * Unit test for the Lookup action.
 * 
 * @see Lookup
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { LookupTestApplication.class, LookupTestConfiguration.class })
@WebAppConfiguration
@IntegrationTest({ "server.port=0" })
public class LookupTest {

    @Value("${local.server.port}")
    public int port;

    /** The action to test. */
    @Autowired
    private Lookup action;

    /** DataPrep ready jackson builder. */
    @Autowired
    private Jackson2ObjectMapperBuilder builder;

    @Test
    public void testAccept() throws Exception {
        assertFalse(action.acceptColumn(new ColumnMetadata()));
    }

    @Test
    public void testName() {
        assertEquals("lookup", action.getName());
    }

    @Test
    public void testCategory() {
        assertEquals("data_blending", action.getCategory());
    }

    @Test
    public void shouldAdapt() {
        // given
        final DataSetMetadata ds = DataSetMetadata.Builder.metadata().name("great dataset").id("ds#123").build();
        String dsUrl = "http://estcequecestbientotleweekend.fr";

        // when
        final Lookup actual = action.adapt(ds, dsUrl);

        // when
        final List<Parameter> parameters = actual.getParameters();
        assertEquals("great dataset", getParamValue(parameters, "lookup_ds_name"));
        assertEquals("ds#123", getParamValue(parameters, "lookup_ds_id"));
        assertEquals(dsUrl, getParamValue(parameters, "lookup_ds_url"));
    }

    @Test
    public void shouldMerge() throws IOException {
        // given
        Map<String, String> parameters = getUsStatesLookupParameters();
        DataSetRow row = ActionMetadataTestUtils.getRow("Atlanta", "GA", "Philips Arena");

        // when
        action.applyOnDataSet(row, new TransformationContext(), parameters);

        // then (check values)
        DataSetRow expected = ActionMetadataTestUtils.getRow("Atlanta", "GA", "Philips Arena", "Georgia", "Atlanta");
        Assert.assertEquals(expected, row);

        // and (check metadata)
        checkMergedMetadata(row.getRowMetadata().getById("0003"), "State", "string", "US_STATE");
        checkMergedMetadata(row.getRowMetadata().getById("0004"), "Capital", "string", "CITY");
    }

    @Test
    public void shouldMergeEmptyCellsOnMissingLookupData() throws IOException {
        // given
        Map<String, String> parameters = getUsStatesLookupParameters();
        DataSetRow row = ActionMetadataTestUtils.getRow("Toronto", "ON", "Air Canada Centre");

        // when
        action.applyOnDataSet(row, new TransformationContext(), parameters);

        // then (value)
        DataSetRow expected = ActionMetadataTestUtils.getRow("Toronto", "ON", "Air Canada Centre", "", "");
        Assert.assertEquals(expected, row);

        // and (metadata)
        checkMergedMetadata(row.getRowMetadata().getById("0003"), "State", "string", "US_STATE");
        checkMergedMetadata(row.getRowMetadata().getById("0004"), "Capital", "string", "CITY");
    }

    @Test
    public void shouldMergeEmptyCellsOnMissingSourceData() throws IOException {
        // given
        Map<String, String> parameters = getUsStatesLookupParameters();
        DataSetRow row = ActionMetadataTestUtils.getRow("Huntington", "", "");

        // when
        action.applyOnDataSet(row, new TransformationContext(), parameters);

        // then (value)
        DataSetRow expected = ActionMetadataTestUtils.getRow("Huntington", "", "", "", "");
        Assert.assertEquals(expected, row);

        // and (metadata)
        checkMergedMetadata(row.getRowMetadata().getById("0003"), "State", "string", "US_STATE");
        checkMergedMetadata(row.getRowMetadata().getById("0004"), "Capital", "string", "CITY");
    }

    @Test
    public void shouldMergeSeveralRows() throws IOException {
        // given
        Map<String, String> parameters = getUsStatesLookupParameters();
        DataSetRow[] rows = new DataSetRow[] { ActionMetadataTestUtils.getRow("Atlanta", "GA", "Philips Arena"),
                ActionMetadataTestUtils.getRow("Miami", "FL", "American Airlines Arena"),
                ActionMetadataTestUtils.getRow("Chicago", "IL", "United Center"),
                ActionMetadataTestUtils.getRow("San Antonio", "TX", "AT&T Center"),
                ActionMetadataTestUtils.getRow("Oakland", "CA", "Oracle Arena") };

        // when
        final TransformationContext transformationContext = new TransformationContext();
        for (DataSetRow row : rows) {
            action.applyOnDataSet(row, transformationContext, parameters);
        }

        // then (check values)
        DataSetRow[] expectedRows = new DataSetRow[] {
                ActionMetadataTestUtils.getRow("Atlanta", "GA", "Philips Arena", "Georgia", "Atlanta"),
                ActionMetadataTestUtils.getRow("Miami", "FL", "American Airlines Arena", "Florida", "Tallahassee"),
                ActionMetadataTestUtils.getRow("Chicago", "IL", "United Center", "Illinois", "Springfield"),
                ActionMetadataTestUtils.getRow("San Antonio", "TX", "AT&T Center", "Texas", "Austin"),
                ActionMetadataTestUtils.getRow("Oakland", "CA", "Oracle Arena", "California", "Sacramento") };
        for (int i = 0; i < rows.length; i++) {
            Assert.assertEquals(expectedRows[i], rows[i]);
            checkMergedMetadata(rows[i].getRowMetadata().getById("0003"), "State", "string", "US_STATE");
            checkMergedMetadata(rows[i].getRowMetadata().getById("0004"), "Capital", "string", "CITY");
        }
    }

    /**
     * Check that the given merged column match the given parameters.
     *
     * @param mergedColumn the column merged by the lookup action
     * @param name the expected column name.
     * @param type the expected column type.
     * @param domain the expected column domain.
     */
    private void checkMergedMetadata(ColumnMetadata mergedColumn, String name, String type, String domain) {
        assertEquals(name, mergedColumn.getName());
        assertEquals(type, mergedColumn.getType());
        assertEquals(domain, mergedColumn.getDomain());
    }


    /**
     * @return the wanted parameter value or null.
     */
    private String getParamValue(List<Parameter> parameters, String name) {
        return parameters.stream() //
                .filter(p -> StringUtils.equals(p.getName(), name)) //
                .map(Parameter::getDefault) //
                .findFirst() //
                .get();
    }

    /**
     * @return US States specific looup parameters.
     */
    private Map<String, String> getUsStatesLookupParameters() throws IOException {
        Map<String, String> parameters = ActionMetadataTestUtils
                .parseParameters(this.getClass().getResourceAsStream("lookupAction.json"));
        parameters.put(LOOKUP_DS_URL.getKey(), "http://localhost:" + port + "/test/lookup/us_states");
        parameters.put(LOOKUP_JOIN_ON.getKey(), "0000");
        parameters.put(COLUMN_ID.getKey(), "0001");
        return parameters;
    }
}
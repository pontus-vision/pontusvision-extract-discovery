(function () {
    'use strict';
    /**
     * @ngdoc service
     * @name data-prep.services.statistics.service:StatisticsService
     * @description Extracts/structures the data to be visualized in charts
     * @requires data-prep.services.playground.service:DatagridService
     * @requires data-prep.services.filter.service:FilterService
     * @requires data-prep.services.utils.service:ConverterService
     * @requires data-prep.services.utils.service:TextFormatService
     */
    function StatisticsService($timeout, DatagridService, FilterService, ConverterService, TextFormatService) {
        var selectedColumn;

        var service = {
            boxplotData: null,
            data: null,
            stateDistribution: null,
            statistics: null,

            processData: processData,
            addFilter: addFilter,
            resetCharts: resetCharts,

            //TODO temporary method to be replaced with new geo chart
            getGeoDistribution: getGeoDistribution
        };

        return service;

        //
        // BELOW ARE ALL THE STATISTICS TABS FUNCTIONS FOR (1-VIZ, 2-VALUES, 3-PATTERN, 4-OTHERS)
        //

        //--------------------------------------------------------------------------------------------------------------
        //-----------------------------------------------1- Visualization-----------------------------------------------
        //--------------------------------------------------------------------------------------------------------------
        /**
         * TEMPORARY : Calculate column value distribution
         * @param columnId
         * @param keyName - distribution key name (default : 'colValue');
         * @param valueName - distribution value name (default : 'frequency')
         * @param keyTransformer - transformer applied to the distribution key
         * @returns {Array} Column distribution array {colValue: string, frequency: integer}}
         * <ul>
         *     <li>colValue (or specified) : the grouped value</li>
         *     <li>frequency (or specified) : the nb of time the value appears</li>
         * </ul>
         */
        function getDistribution(columnId, keyName, valueName, keyTransformer) {
            keyName = keyName || 'colValue';
            valueName = valueName || 'frequency';

            var records = DatagridService.data.records;

            return _.chain(records)
                .groupBy(function (item) {
                    return item[columnId];
                })
                .map(function (val, index) {
                    var item = {};
                    item[keyName] = keyTransformer ? keyTransformer(index) : index;
                    item[valueName] = val.length;
                    return item;
                })
                .sortBy(valueName)
                .reverse()
                .value();
        }

        /**
         * TEMPORARY : Calculate geo distribution, and targeted map
         * @param {object} column The target column
         * @returns {object} Geo distribution {map: string, data: [{}]}
         */
        function getGeoDistribution(column) {
            var keyPrefix = 'us-';
            var map = 'countries/us/us-all';

            return {
                map: map,
                data: getDistribution(column.id, 'hc-key', 'value', function (key) {
                    return keyPrefix + key.toLowerCase();
                })
            };
        }

        /**
         * @ngdoc method
         * @name initRangeHistogram
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @param {Array} histoData Array of data
         * @description Adapt the numeric range data to fit histogram format
         */
        function initRangeHistogram(histoData) {
            var concatData = [];
            _.each(histoData, function (histDatum) {
                concatData.push({
                    'data': histDatum.range.min + ' ... ' + histDatum.range.max,
                    'formattedValue': TextFormatService.computeHTMLForLeadingOrTrailingHiddenChars(histDatum.range.min + ' ... ' + histDatum.range.max),
                    'occurrences': histDatum.occurrences
                });
            });

            service.data = concatData;
        }

        /**
         * @ngdoc method
         * @name initClassicHistogram
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @param {Array} frequencyTable The frequency table
         * @description Set the frequency table that fit the historgram format
         */
        function initClassicHistogram(frequencyTable) {
            service.data = _.map(frequencyTable,function(rec){
                //The formatted Data which will be shown and not filtered
                rec.formattedValue = TextFormatService.computeHTMLForLeadingOrTrailingHiddenChars(rec.data);
                return rec;
            });
        }

        //--------------------------------------------------------------------------------------------------------------
        //-------------------------------------------------2- Values----------------------------------------------------
        //--------------------------------------------------------------------------------------------------------------
        /**
         * @ngdoc method
         * @name clean
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @param {number} value Value to clean the float
         * @description Cleans the value to have 2 decimals (5.2568845842587425588 -> 5.25)
         * @returns {number} The value in the clean format
         */
        function clean(value) {
            return value === parseInt(value, 10) ? value : +value.toFixed(2);
        }

        /**
         * @ngdoc method
         * @name initStatistics
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @param {object} column The target column
         * @description Initialize the statistics to display in the values TAB of the stats part
         */
        function initValuesStatistics(column) {
            if (!column.statistics) {
                return;
            }

            var stats = column.statistics;
            var colType = ConverterService.simplifyType(column.type);
            var commonStats = {
                COUNT: stats.count,
                DISTINCT_COUNT: stats.distinctCount,
                DUPLICATE_COUNT: stats.duplicateCount,

                VALID: stats.valid,
                EMPTY: stats.empty,
                INVALID: stats.invalid
            };

            var specificStats = {};
            switch (colType) {
                case 'number':
                    specificStats.MIN = clean(stats.min);
                    specificStats.MAX = clean(stats.max);
                    specificStats.MEAN = clean(stats.mean);
                    specificStats.VARIANCE = clean(stats.variance);
                    if (stats.quantiles.lowerQuantile !== 'NaN') {
                        specificStats.MEDIAN = clean(stats.quantiles.median);
                        specificStats.LOWER_QUANTILE = clean(stats.quantiles.lowerQuantile);
                        specificStats.UPPER_QUANTILE = clean(stats.quantiles.upperQuantile);
                    }

                    break;
                case 'text':
                    specificStats.AVG_LENGTH = clean(stats.textLengthSummary.averageLength);
                    specificStats.AVG_LENGTH_WITH_BLANK = clean(stats.textLengthSummary.averageLengthWithBlank);
                    specificStats.MIN_LENGTH = stats.textLengthSummary.minimalLength;
                    specificStats.MIN_LENGTH_WITH_BLANK = stats.textLengthSummary.minimalLengthWithBlank;
                    specificStats.MAX_LENGTH = stats.textLengthSummary.maximalLength;
                    break;
            }

            service.statistics = {
                common: commonStats,
                specific: specificStats
            };
        }

        //--------------------------------------------------------------------------------------------------------------
        //-------------------------------------------------3- Pattern---------------------------------------------------
        //--------------------------------------------------------------------------------------------------------------

        //Currently there are no stats to be brought on the pattern data

        //--------------------------------------------------------------------------------------------------------------
        //-------------------------------------------------4- Others----------------------------------------------------
        //--------------------------------------------------------------------------------------------------------------
        /**
         * @ngdoc method
         * @name updateBoxplotData
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @description Gathers the boxplot data from the specific stats of the columns having a 'number' type
         */
        function updateBoxplotData() {
            var specStats = service.statistics.specific;

            //waiting for DQ to process negative values
            if (specStats.LOWER_QUANTILE) {
                service.boxplotData = {
                    min: specStats.MIN,
                    max: specStats.MAX,
                    q1: specStats.LOWER_QUANTILE,
                    q2: specStats.UPPER_QUANTILE,
                    median: specStats.MEDIAN,
                    mean: specStats.MEAN,
                    variance: specStats.VARIANCE
                };
            }
            else {
                service.boxplotData = null;
            }
        }

        //--------------------------------------------------------------------------------------------------------------
        //-------------------------------------------------COMMON-------------------------------------------------------
        //--------------------------------------------------------------------------------------------------------------
        /**
         * @ngdoc method
         * @name addFilter
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @param {string} value The phrase to filter
         * @description Add a filter in the angular context
         */
        function addFilter(value) {
            var column = service.selectedColumn;
            var filterFn = value ?
                FilterService.addFilter.bind(null, 'contains', column.id, column.name, {phrase: value}) :
                FilterService.addFilter.bind(null, 'empty_records', column.id, column.name, {});

            $timeout(filterFn);
        }

        /**
         * @ngdoc method
         * @name processMapData
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @param {object} column The column to visualize
         * @description Remove the previous charts data and set the map chart
         */
        function processMapData(column) {
            selectedColumn = column;
            //remove the boxplot
            service.boxplotData = null;
            //remove the barchart
            service.data = null;
            //show the map
            service.stateDistribution = column;
        }

        /**
         * @ngdoc method
         * @name processNonMapData
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @param {object} column The column to visualize
         * @description Reset the map chart and calculate the needed data for visualization
         */
        function processNonMapData(column) {
            service.selectedColumn = column;
            service.boxplotData = null;
            service.data = null;
            service.stateDistribution = null;

            switch (ConverterService.simplifyType(column.type)) {
                case 'number':
                    initRangeHistogram(column.statistics.histogram);
                    updateBoxplotData();
                    break;
                case 'text':
                case 'boolean':
                    initClassicHistogram(column.statistics.frequencyTable);
                    break;
                default :
                    console.log('nor a number neither a boolean neither a string');
                    service.boxplotData = null;
            }
        }

        /**
         * @ngdoc method
         * @name processData
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @param {object} column The column to visualize
         * @description Processes the statistics data for visualization on the provided column
         */
        function processData(column) {
            initValuesStatistics(column);

            //TODO replace with new geo chart
            if (column.domain.indexOf('STATE_CODE') !== -1) {
                processMapData(column);
            }
            //TODO Coming soon after the integration of the globe map : reset charts and init localization chart data
            // else if (column.domain === 'LOCALIZATION') {
            //    processLocalizationMapData(column);
            //}
            else {
                processNonMapData(column);
            }
        }

        /**
         * @ngdoc method
         * @name resetCharts
         * @methodOf data-prep.services.statistics.service:StatisticsService
         * @description Removes all the data to disable all visualization
         */
        function resetCharts() {
            service.boxplotData = null;
            service.data = null;
            service.stateDistribution = null;
            service.statistics = null;
        }
    }

    angular.module('data-prep.services.statistics')
        .service('StatisticsService', StatisticsService);
})();
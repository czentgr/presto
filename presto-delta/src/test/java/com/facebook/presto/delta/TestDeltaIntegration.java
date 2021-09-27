/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.delta;

import com.google.common.base.Joiner;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

/**
 * Integration tests for reading Delta tables.
 */
public class TestDeltaIntegration
        extends AbstractDeltaDistributedQueryTestBase
{
    @Test
    public void readPrimitiveTypeData()
    {
        // Test reading following primitive types from a Delta table (all ints, float, double, decimal, boolean, varchar, varbinary)
        String testQuery =
                format("SELECT * FROM \"%s\".\"%s\"", PATH_SCHEMA, goldenTablePath("data-reader-primitives"));
        String expResultsQuery = getPrimitiveTypeTableData();
        assertQuery(testQuery, expResultsQuery);
    }

    @Test
    public void readArrayTypeData()
    {
        // Test reading following array elements with type (all ints, float, double, decimal, boolean, varchar, varbinary)
        String testQuery =
                format("SELECT * FROM \"%s\".\"%s\"", PATH_SCHEMA, goldenTablePath("data-reader-array-primitives"));

        // Create query for the expected results.
        List<String> expRows = new ArrayList<>();
        for (byte i = 0; i < 10; i++) {
            expRows.add(format("SELECT " +
                    "   array[cast(%s as integer)]," +
                    "   array[cast(%s as bigint)]," +
                    "   array[cast(%s as tinyint)]," +
                    "   array[cast(%s as smallint)]," +
                    "   array[%s]," +
                    "   array[cast(%s as real)]," +
                    "   array[cast(%s as double)], " +
                    "   array['%s'], " +
                    "   array[cast(X'0%s0%s' as varbinary)], " +
                    "   array[cast(%s as decimal)]", i, i, i, i, (i % 2 == 0 ? "true" : "false"), i, i, i, i, i, i));
        }
        String expResultsQuery = Joiner.on(" UNION ").join(expRows);

        assertQuery(testQuery, expResultsQuery);
    }

    @Test
    public void readMapTypeData()
    {
        // Test reading MAP data type columns from Delta table
        String testQuery =
                format("SELECT map_keys(a), map_values(e) FROM \"%s\".\"%s\"", PATH_SCHEMA, goldenTablePath("data-reader-map"));

        List<String> expRows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            expRows.add("SELECT " +
                    "   ARRAY[cast(" + i + " as integer)]," +
                    "   ARRAY[cast(" + i + " as decimal)]");
        }
        String expResultsQuery = Joiner.on(" UNION ").join(expRows);

        assertQuery(testQuery, expResultsQuery);
    }

    @Test
    public void readTableRegisteredInHMS()
    {
        String deltaTable = "data-reader-primitives";
        String testQuery = format("SELECT * FROM \"%s\"", deltaTable);
        String expResultsQuery = getPrimitiveTypeTableData();
        assertDeltaQuery(deltaTable, testQuery, expResultsQuery);
    }

    @Test
    public void readSpecificSnapshot()
            throws Exception
    {
        String deltaTable = "snapshot-data3";
        String testQueryTemplate = "SELECT * FROM \"%s@%s\" WHERE col1 = 0";

        // read snapshot version 2
        String testQueryV2 = format(testQueryTemplate, deltaTable, "v2");
        String expResultsQueryV2 = "SELECT * FROM VALUES(0, 'data-2-0')";
        assertDeltaQuery(deltaTable, testQueryV2, expResultsQueryV2);

        // read snapshot version 3
        String testQueryV3 = format(testQueryTemplate, deltaTable, "v3");
        String expResultsQueryV3 = "SELECT * FROM VALUES(0, 'data-2-0'), (0, 'data-3-0')";
        assertDeltaQuery(deltaTable, testQueryV3, expResultsQueryV3);

        // Delta library looks at the last modification time of the checkpoint and commit files
        // to figure out when the snapshot is created. In the tests, as the test files are copied
        // to target location, the modification time will be the time when they are copied and not when
        // the snapshot is created. In order to test reading the snapshot version for a given timestamp,
        // manually update the modification time of the commit and checkpoint files.
        // 1637274601000L millis = 2021-11-18 10:30:01
        String deltaTableLocation = goldenTablePath(deltaTable);
        setCommitFileModificationTime(deltaTableLocation, 0, 1637231401000L);
        setCommitFileModificationTime(deltaTableLocation, 1, 1637231402000L);
        setCommitFileModificationTime(deltaTableLocation, 2, 1637231405000L);
        setCommitFileModificationTime(deltaTableLocation, 3, 1637231407000L);

        // read snapshot as of 2020-10-26 02:50:00 - this should fail as there are no snapshots before this timestamp
        String testQueryTs1 = format(testQueryTemplate, deltaTable, "t2020-10-27 02:50:00");
        assertDeltaQueryFails(
                deltaTable,
                testQueryTs1,
                "There is no snapshot exists in Delta table 'deltatables.snapshot-data3@t2020-10-27 02:50:00' " +
                        "that is created on or before '2020-10-27T02:50:00Z'");

        // read snapshot as of 2021-11-18 10:30:02 - this should read the data from commit id 1.
        String testQueryTs2 = format(testQueryTemplate, deltaTable, "t2021-11-18 10:30:02");
        String expResultsQueryTs2 = "SELECT * FROM VALUES(0, 'data-0-0'), (0, 'data-1-0')";
        assertDeltaQuery(deltaTable, testQueryTs2, expResultsQueryTs2);

        // read snapshot as of 2021-11-18 10:30:07 - this should read the data from the latest commit
        String testQueryTs3 = format(testQueryTemplate, deltaTable, "t2021-11-18 10:30:07");
        String expResultsQueryTs3 = "SELECT * FROM VALUES(0, 'data-2-0'), (0, 'data-3-0')";
        assertDeltaQuery(deltaTable, testQueryTs3, expResultsQueryTs3);
    }

    @Test(enabled = false) // Enable once the bug in Delta library is fixed
    public void readCheckpointedDeltaTable()
    {
        // Delta table commits are periodically checkpointed into a parquet file.
        // Test Delta connector is able to read the checkpointed commits in a parquet file.
        // Test table has commit files (0-10) deleted. So it has to rely on reading the Parquet file
        // to fetch the files latest commit (i.e > 10).
        String deltaTable = "checkpointed-delta-table";
        String testQueryTemplate = "SELECT * FROM \"%s%s\" WHERE col1 in (0, 10, 15)";

        // read snapshot version 3 - expect can't time travel error
        String testQueryV3 = format(testQueryTemplate, deltaTable, "@v3");
        assertDeltaQueryFails(
                deltaTable,
                testQueryV3,
                "Can not find snapshot \\(3\\) in Delta table 'deltatables.checkpointed-delta-table\\@v3': No reproducible commits found at .*");

        // read latest data
        String testQueryLatest = format(testQueryTemplate, deltaTable, "");
        String expResultsQueryLatest = "SELECT * FROM VALUES(0, 'data-0-0'), (10, 'data-0-10'), (15, 'data-0-15')";
        assertDeltaQuery(deltaTable, testQueryLatest, expResultsQueryLatest);

        // read snapshot version 13
        String testQueryV13 = format(testQueryTemplate, deltaTable, "@v13");
        String expResultsQueryV13 = "SELECT * FROM VALUES(0, 'data-0-0'), (10, 'data-0-10')";
        assertDeltaQuery(deltaTable, testQueryV13, expResultsQueryV13);
    }

    /**
     * Expected results for table "data-reader-primitives"
     */
    private static String getPrimitiveTypeTableData()
    {
        // Create query for the expected results.
        List<String> expRows = new ArrayList<>();
        for (byte i = 0; i < 10; i++) {
            expRows.add(format("SELECT " +
                    "   cast(%s as integer)," +
                    "   cast(%s as bigint)," +
                    "   cast(%s as tinyint)," +
                    "   cast(%s as smallint)," +
                    "   %s," +
                    "   cast(%s as real)," +
                    "   cast(%s as double), " +
                    "   '%s', " +
                    "   cast(X'0%s0%s' as varbinary), " +
                    "   cast(%s as decimal)", i, i, i, i, (i % 2 == 0 ? "true" : "false"), i, i, i, i, i, i));
        }
        expRows.add("SELECT null, null, null, null, null, null, null, null, null, null");
        return Joiner.on(" UNION ").join(expRows);
    }

    private static void setCommitFileModificationTime(String tableLocation, long commitId, long commitTimeMillis)
            throws Exception
    {
        Files.setLastModifiedTime(
                Paths.get(URI.create(tableLocation)).resolve("_delta_log/").resolve(format("%020d.json", commitId)),
                FileTime.from(commitTimeMillis, TimeUnit.MILLISECONDS));
    }
}
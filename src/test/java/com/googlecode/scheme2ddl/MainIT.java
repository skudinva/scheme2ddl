package com.googlecode.scheme2ddl;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author A_Reshetnikov
 * @since Date: 17.09.2016
 */

@SpringBootTest(classes = ConfigurationIT.class, properties = "test-default.properties")
public class MainIT extends AbstractTestNGSpringContextTests {

    @Value("${hrUrl}")
    private String url;


    @Autowired
    private JdbcTemplate dbaJdbcTemplate;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    private final PrintStream outOriginal = System.out;
    private final PrintStream errorOriginal = System.err;


    @BeforeClass
    public void setUp()  {
        try {
            dbaJdbcTemplate.execute("ALTER USER HR ACCOUNT UNLOCK IDENTIFIED BY pass");
        }
        catch (CannotGetJdbcConnectionException e){
            e.printStackTrace();
            throw new SkipException("Ignore all test due " +  e.getMessage());
        }

    }

    @BeforeMethod
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterMethod
    public void cleanUpStreams() {
        System.setOut(outOriginal);
        System.setErr(errorOriginal);
        outContent.reset();
        errContent.reset();
    }

    @Test(dataProvider = "justTestConnectionParamNames")
    public void testMainJustTestConnectionOption(String urlParamName, String testConnParamName) throws Exception {
        String[] args = {urlParamName, url, testConnParamName};
        Main.main(args);
        Assert.assertEquals(
                outContent.toString(),
                "OK success connection to jdbc:oracle:thin:" + url + "\n"
        );
    }

    @DataProvider
    public static Object[][] justTestConnectionParamNames() {
        return new Object[][]{
                {"-url", "-tc"},
                {"-url", "--test-connection"},
        };
    }


    @Test
    public void testPrintVersionOption() throws Exception {
        String[] args = {"-version"};
        Main.main(args);
        assertThat(outContent.toString(), containsString("scheme2ddl version "));
    }

    @Test(expectedExceptions = Exception.class, expectedExceptionsMessageRegExp = "Job (.*) unsuccessful", enabled = false)
    public void testStopOnWarning() throws Exception {
        String[] args = {"-url", url, "--stop-on-warning"};
        Main.main(args);

    }

    @Test
    public void testExportHRSchemaDefault() throws Exception {
        String[] args = {"-url", url};
        Main.main(args);
        String out = outContent.toString();
        String pwd = FileUtils.getFile(new File("output")).getAbsolutePath();
        assertThat(out, containsString("Will try to process schema  [HR]"));
        assertThat(out, containsString("Start getting of user object list in schema HR for processing"));
        assertThat(out, containsString("WARNING: processing of 'PUBLIC DATABASE LINK' will be skipped because HR no access to view it"));
        assertThat(out, containsString("Found 34 items for processing in schema HR"));
        assertThat(out, containsString(String.format("Saved sequence hr.locations_seq to file %s/sequences/locations_seq.sql", pwd)));
        assertThat(out, containsString(String.format("Saved sequence hr.employees_seq to file %s/sequences/employees_seq.sql", pwd)));
        assertThat(out, containsString(String.format("Saved trigger hr.update_job_history to file %s/triggers/update_job_history.sql", pwd)));
        assertThat(out, containsString(String.format("Saved procedure hr.add_job_history to file %s/procedures/add_job_history.sql", pwd)));
        assertThat(out, containsString(String.format("Saved table hr.locations to file %s/tables/locations.sql", pwd)));
        assertThat(out, containsString(String.format("Saved procedure hr.secure_dml to file %s/procedures/secure_dml.sql", pwd)));
        assertThat(out, containsString(String.format("Saved view hr.emp_details_view to file %s/views/emp_details_view.sql", pwd)));


       assertThat(out, containsString(
               "-------------------------------------------------------\n" +
               "   R E P O R T     S K I P P E D     O B J E C T S     \n" +
               "-------------------------------------------------------\n" +
               "| skip rule |  object type              |    count    |\n" +
               "-------------------------------------------------------\n" +
               "|  config   |  INDEX                    |      19     |"));


        assertThat(out, containsString("Written 15 ddls with user objects from total 34 in schema HR"));
        assertThat(out, containsString("Skip processing 19 user objects from total 34 in schema HR"));
        assertThat(out, containsString("scheme2ddl of schema HR completed"));


        assertEqualsFileContent(pwd + "/sequences/locations_seq.sql", "CREATE SEQUENCE  \"HR\".\"LOCATIONS_SEQ\"" +
                "  MINVALUE 1 MAXVALUE 9900 INCREMENT BY 100 START WITH 3300 NOCACHE  NOORDER  NOCYCLE ;");


        assertEqualsFileContent(pwd + "/tables/regions.sql", "CREATE TABLE \"HR\".\"REGIONS\" \n" +
                "   (\t\"REGION_ID\" NUMBER CONSTRAINT \"REGION_ID_NN\" NOT NULL ENABLE, \n" +
                "\t\"REGION_NAME\" VARCHAR2(25)\n" +
                "   ) ;\n" +
                "  ALTER TABLE \"HR\".\"REGIONS\" ADD CONSTRAINT \"REG_ID_PK\" PRIMARY KEY (\"REGION_ID\") ENABLE;\n" +
                "  CREATE UNIQUE INDEX \"HR\".\"REG_ID_PK\" ON \"HR\".\"REGIONS\" (\"REGION_ID\") \n" +
                "  ;");


    }


    private static void assertEqualsFileContent(String path, String content) throws IOException {
        File file = new File(path);
        assertTrue(file.exists());
        String fileContent = FileUtils.readFileToString(file, "UTF-8");
        assertEquals(fileContent.trim().replace("\r", ""), content.replace("\r", ""));

    }
}
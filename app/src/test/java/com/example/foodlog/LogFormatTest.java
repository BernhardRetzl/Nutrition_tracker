package com.example.foodlog;
import org.junit.Test;
import static org.junit.Assert.*;
public class LogFormatTest {
    @Test public void normalizesWeightsAndAllowsClearing() {
        assertEquals("250",LogFormat.weight("250"));
        assertEquals("300.5",LogFormat.weight("300,500"));
        assertEquals("",LogFormat.weight(" "));
        assertEquals("0.125",LogFormat.weight("0.125"));
    }
    @Test public void rejectsInvalidWeights() {
        for(String value:new String[]{"-2","0","NaN","1e3","1.0001","1000001",".","2,3,4"})
            assertThrows(IllegalArgumentException.class,()->LogFormat.weight(value));
    }
    @Test public void exportsQuotedCsvAndProtectsSpreadsheetCells() {
        assertEquals("\"2026-11-16\",\"Milk, \"\"whole\"\"\",250\r\n",LogFormat.csvRow("2026-11-16","Milk, \"whole\"","250"));
        assertEquals("\"'=1+1\"",LogFormat.csvCell("=1+1"));
    }
    @Test public void addsWeightsExactlyAndValidatesTotal() {
        assertEquals("193",LogFormat.addWeight("125","68"));
        assertEquals("68",LogFormat.addWeight("","68"));
        assertEquals("0.3",LogFormat.addWeight("0.1","0,2"));
        assertEquals("125.125",LogFormat.addWeight("125","0.125"));
        for(String extra:new String[]{"","0","-2","1.0001"})
            assertThrows(IllegalArgumentException.class,()->LogFormat.addWeight("125",extra));
        assertThrows(IllegalArgumentException.class,()->LogFormat.addWeight("999999","2"));
    }
}

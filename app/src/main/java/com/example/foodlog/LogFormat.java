package com.example.foodlog;

import java.math.BigDecimal;

public final class LogFormat {
    private LogFormat() {}
    public static String weight(String input) {
        String value = input.trim().replace(',', '.');
        if (value.isEmpty()) return "";
        if (!value.matches("[0-9]+(?:\\.[0-9]{0,3})?"))
            throw new IllegalArgumentException("Enter grams, with up to 3 decimal places");
        BigDecimal grams = new BigDecimal(value);
        if (grams.signum() <= 0 || grams.compareTo(new BigDecimal("1000000")) > 0)
            throw new IllegalArgumentException("Enter a weight above 0 and up to 1,000,000 g");
        return grams.stripTrailingZeros().toPlainString();
    }
    public static String addWeight(String current, String additional) {
        String base=weight(current), extra=weight(additional);
        if(extra.isEmpty())throw new IllegalArgumentException("Enter grams to add");
        BigDecimal total=(base.isEmpty()?BigDecimal.ZERO:new BigDecimal(base)).add(new BigDecimal(extra));
        return weight(total.toPlainString());
    }
    public static String csvCell(String input) {
        // Prevent spreadsheet formula evaluation when opening an exported food name.
        String value = input;
        if (value.stripLeading().matches("^[=+@-].*") || value.startsWith("\t") || value.startsWith("\r")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    public static String csvRow(String date, String food, String grams) {
        return csvCell(date) + "," + csvCell(food) + "," + grams + "\r\n";
    }
}

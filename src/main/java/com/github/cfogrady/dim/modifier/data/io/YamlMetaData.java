package com.github.cfogrady.dim.modifier.data.io;

import lombok.Data;

@Data
public class YamlMetaData {
    private boolean isBem;
    private int id;
    private int revision;
    private int year;
    private int month;
    private int day;
    private int originalChecksum;
}

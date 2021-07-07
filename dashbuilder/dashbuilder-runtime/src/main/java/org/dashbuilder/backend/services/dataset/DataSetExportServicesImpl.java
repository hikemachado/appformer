package org.dashbuilder.backend.services.dataset;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.DateFormatConverter;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.dashbuilder.DataSetCore;
import org.dashbuilder.backend.exception.ExceptionManager;
import org.dashbuilder.dataset.DataColumn;
import org.dashbuilder.dataset.DataSet;
import org.dashbuilder.dataset.DataSetLookup;
import org.dashbuilder.dataset.DataSetManager;
import org.dashbuilder.dataset.def.DataSetDefRegistry;
import org.dashbuilder.dataset.group.Interval;
import org.dashbuilder.dataset.service.DataSetExportServices;
import org.dashbuilder.dataset.uuid.UUIDGenerator;
import org.jboss.errai.bus.server.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.java.nio.file.FileSystem;
import org.uberfire.java.nio.file.FileSystems;
import org.uberfire.java.nio.file.Files;
import org.uberfire.java.nio.file.Path;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

@ApplicationScoped
@Service
public class DataSetExportServicesImpl implements DataSetExportServices{

    private static final String TEXT_CELL = "text_cell";
    protected static Logger log = LoggerFactory.getLogger(DataSetExportServicesImpl.class);
    protected DataSetManager dataSetManager;
    protected DataSetDefRegistry gitStorage;
    protected UUIDGenerator uuidGenerator;
    protected ExceptionManager exceptionManager;

    protected String DEFAULT_SEPARATOR_CHAR = ";";
    protected String DEFAULT_QUOTE_CHAR = "\"";
    protected String DEFAULT_ESCAPE_CHAR = "\\";

    protected String dateFormatPattern = "dd/MM/yyyy HH:mm:ss";
    protected String numberFormatPattern = "#,###.##########";

    protected DecimalFormat decf = new DecimalFormat(numberFormatPattern);
    protected DateFormat datef = new SimpleDateFormat(dateFormatPattern);


    FileSystem fileSystem;

    public DataSetExportServicesImpl() {
    }

    @Inject
    public DataSetExportServicesImpl(DataSetManager dataSetManager,
                                     //@Named("datasetsFS") FileSystem fileSystem,
                                     ExceptionManager exceptionManager) {
        this.dataSetManager = dataSetManager;
        Map env = new HashMap<String, Object>();
        this.fileSystem = FileSystems.getFileSystem(URI.create("file:///"));
        this.uuidGenerator = DataSetCore.get().getUuidGenerator();
        this.exceptionManager = exceptionManager;
    }

    public org.uberfire.backend.vfs.Path exportDataSetCSV(DataSetLookup lookup) {
        DataSet dataSet = dataSetManager.lookupDataSet(lookup);
        return exportDataSetCSV(dataSet);
    }

    public org.uberfire.backend.vfs.Path exportDataSetCSV(DataSet dataSet) {
        try {
            if (dataSet == null) {
                throw new IllegalArgumentException("Null dataSet specified!");
            }
            int columnCount = dataSet.getColumns().size();
            int rowCount = dataSet.getRowCount();

            List<String[]> lines = new ArrayList<>(rowCount+1);

            String[] line = new String[columnCount];
            for (int cc = 0; cc < columnCount; cc++) {
                DataColumn dc = dataSet.getColumnByIndex(cc);
                line[cc] = dc.getId();
            }
            lines.add(line);

            for (int rc = 0; rc < rowCount; rc++) {
                line = new String[columnCount];
                for (int cc = 0; cc < columnCount; cc++) {
                    line[cc] = formatAsString(dataSet.getValueAt(rc, cc));
                }
                lines.add(line);
            }

            String tempCsvFile = uuidGenerator.newUuid() + ".csv";
            if(fileSystem != null){
                //log.info("FILE SYSTEM: " + fileSystem.getName());
                if(fileSystem.getRootDirectories() != null){
                    for(Path pa : fileSystem.getRootDirectories()){
                        log.info("PATH: " + pa.toUri().getPath());
                    }

                }
            }
            Path tempCsvPath = fileSystem.getRootDirectories().iterator().next().resolve("tmp").resolve(tempCsvFile);

            //java.nio.file.Path tempCsvPath = java.nio.file.Paths.get("/home/henrique").resolve(tempCsvFile);
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(tempCsvPath)));
                 CSVWriter writer = new CSVWriter(bw,
                         DEFAULT_SEPARATOR_CHAR.charAt(0),
                         DEFAULT_QUOTE_CHAR.charAt(0),
                         DEFAULT_ESCAPE_CHAR.charAt(0))
            ) {
                writer.writeAll(lines);
                writer.flush();
            }

            return Paths.convert(tempCsvPath);
        }
        catch (Exception e) {
            throw exceptionManager.handleException(e);
        }
    }

    @Override
    public org.uberfire.backend.vfs.Path exportDataSetExcel(DataSetLookup dataSetLookup) {
        DataSet dataSet = dataSetManager.lookupDataSet( dataSetLookup );
        return exportDataSetExcel( dataSet );
    }

    @Override
    public org.uberfire.backend.vfs.Path exportDataSetExcel(DataSet dataSet) {
        try {
            SXSSFWorkbook wb = dataSetToWorkbook(dataSet);

            // Write workbook to Path
            String tempXlsFile = uuidGenerator.newUuid() + ".xlsx";
            Path tempXlsPath = fileSystem.getRootDirectories().iterator().next().resolve("tmp").resolve(tempXlsFile);
            try (OutputStream os = Files.newOutputStream(tempXlsPath)) {
                wb.write(os);
                os.flush();
            }

            // Dispose of temporary files backing this workbook on disk
            if (!wb.dispose()) {
                log.warn("Could not dispose of temporary file associated to data export!");
            }
            return Paths.convert(tempXlsPath);
        } catch (Exception e) {
            throw exceptionManager.handleException(e);
        }
    }

    //Package private to enable testing
    SXSSFWorkbook dataSetToWorkbook(DataSet dataSet) {
        // TODO?: Excel 2010 limits: 1,048,576 rows by 16,384 columns; row width 255 characters
        if (dataSet == null) {
            throw new IllegalArgumentException("Null dataSet specified!");
        }
        int columnCount = dataSet.getColumns().size();
        int rowCount = dataSet.getRowCount() + 1; //Include header row;
        int row = 0;

        SXSSFWorkbook wb = new SXSSFWorkbook(100); // keep 100 rows in memory, exceeding rows will be flushed to disk
        Map<String, CellStyle> styles = createStyles(wb);
        SXSSFSheet sh = wb.createSheet("Sheet 1");

        // General setup
        sh.setDisplayGridlines(true);
        sh.setPrintGridlines(false);
        sh.setFitToPage(true);
        sh.setHorizontallyCenter(true);
        sh.trackAllColumnsForAutoSizing();
        PrintSetup printSetup = sh.getPrintSetup();
        printSetup.setLandscape(true);

        // Create header
        Row header = sh.createRow(row++);
        header.setHeightInPoints(20f);
        for (int i = 0; i < columnCount; i++) {
            Cell cell = header.createCell(i);
            cell.setCellStyle(styles.get("header"));
            cell.setCellValue(dataSet.getColumnByIndex(i).getId());
        }

        // Create data rows
        for (; row < rowCount; row++) {
            Row _row = sh.createRow(row);
            for (int cellnum = 0; cellnum < columnCount; cellnum++) {
                Cell cell = _row.createCell(cellnum);
                Object value = dataSet.getValueAt(row - 1,
                        cellnum);
                if (value instanceof Short || value instanceof Long || value instanceof Integer || value instanceof BigInteger) {
                    cell.setCellType(CellType.NUMERIC);
                    cell.setCellStyle(styles.get("integer_number_cell"));
                    cell.setCellValue(((Number) value).doubleValue());
                } else if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
                    cell.setCellType(CellType.NUMERIC);
                    cell.setCellStyle(styles.get("decimal_number_cell"));
                    cell.setCellValue(((Number) value).doubleValue());
                } else if (value instanceof Date) {
                    cell.setCellType(CellType.STRING);
                    cell.setCellStyle(styles.get("date_cell"));
                    cell.setCellValue((Date) value);
                } else if (value instanceof Interval) {
                    cell.setCellType(CellType.STRING);
                    cell.setCellStyle(styles.get(TEXT_CELL));
                    cell.setCellValue(((Interval) value).getName());
                } else {
                    cell.setCellType(CellType.STRING);
                    cell.setCellStyle(styles.get(TEXT_CELL));
                    String val = value == null ? "" : value.toString();
                    cell.setCellValue(val);
                }
            }
        }

        // Adjust column size
        for (int i = 0; i < columnCount; i++) {
            sh.autoSizeColumn(i);
        }
        return wb;
    }

    private String formatAsString(Object value) {
        if (value == null) return "";
        if (value instanceof Number) return decf.format(value);
        else if (value instanceof Date) return datef.format(value);
            // TODO verify if this is correct
        else if (value instanceof Interval) return ((Interval)value).getName();
        else return value.toString();
    }

    private Map<String, CellStyle> createStyles(Workbook wb){
        Map<String, CellStyle> styles = new HashMap<>();
        CellStyle style;

        Font titleFont = wb.createFont();
        titleFont.setFontHeightInPoints((short)12);
        titleFont.setBold(true);
        style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor( IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setFont(titleFont);
        style.setWrapText(false);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_80_PERCENT.getIndex());
        styles.put("header", style);

        Font cellFont = wb.createFont();
        cellFont.setFontHeightInPoints((short)10);
        cellFont.setBold(true);

        style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.BOTTOM);
        style.setFont(cellFont);
        style.setWrapText(false);
        style.setDataFormat(wb.createDataFormat().getFormat( BuiltinFormats.getBuiltinFormat( 3 )));
        styles.put("integer_number_cell", style);

        style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.BOTTOM);
        style.setFont(cellFont);
        style.setWrapText(false);
        style.setDataFormat(wb.createDataFormat().getFormat(BuiltinFormats.getBuiltinFormat(4)));
        styles.put("decimal_number_cell", style);

        style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.BOTTOM);
        style.setFont(cellFont);
        style.setWrapText(false);
        style.setDataFormat( (short) BuiltinFormats.getBuiltinFormat("text") );
        styles.put(TEXT_CELL, style);

        style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.BOTTOM);
        style.setFont(cellFont);
        style.setWrapText(false);
        style.setDataFormat(wb.createDataFormat().getFormat( DateFormatConverter.convert( Locale.getDefault(), dateFormatPattern )));
        styles.put("date_cell", style);
        return styles;
    }
}

package org.dashbuilder.backend;

import org.dashbuilder.dataset.DataSet;
import org.dashbuilder.dataset.DataSetBuilder;
import org.dashbuilder.dataset.DataSetFactory;
import org.dashbuilder.dataset.DataSetGenerator;

import javax.enterprise.context.ApplicationScoped;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

@ApplicationScoped
public class Neo4jDataSetGenerator implements DataSetGenerator {
    @Override
    public DataSet buildDataSet(Map<String, String> params) {
        DataSource ds = null;
        Connection con = null;
        PreparedStatement stmt = null;
        InitialContext ic;

        DataSetBuilder builder = DataSetFactory.newDataSetBuilder();


        String columnsParam = params.get("columns");
        String[] columns  = columnsParam.split(",");

        String[] results = new String[columns.length];

        for(int i = 0; i < columns.length; i++) {

            builder.label(columns[i]);
        }

        try {

            ic = new InitialContext();
            ds = (DataSource)ic.lookup( EnvProperties.getInstace().getProperty("neo4j.datasource") );
            con = ds.getConnection();
            // Querying
            String query = params.get("query");
            stmt = con.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();



            while (rs.next()) {
                //System.out.println("Friend: "+rs.getString("name")+" is "+rs.getString("age"));
                for(int i = 0; i < columns.length; i++) {

                    results[i] = rs.getString(columns[i]);
                }
                builder.row(results);
            }
            rs.close();
            stmt.close();
        }catch(Exception e){
            System.out.println("Exception thrown " +e);
        }finally{
            if(con != null){
                try {
                    con.close();
                } catch (Exception e1) {
                    System.out.println("CON Exception thrown " +e1);
                }
            }
        }

    	/*DataSetBuilder builder = DataSetFactory.newDataSetBuilder()
                .label("Name")
                .label("Age");
                .date(CLOSING_DATE)
                .label(PIPELINE)
                .label(STATUS)
                .label(CUSTOMER)
                .label(COUNTRY)
                .label(PRODUCT)
                .label(SALES_PERSON)
                .number(PROBABILITY)
                .label(SOURCE)
                .number(EXPECTED_AMOUNT)
                .label(COLOR);*/

        /*for (int year = startYear; year <= endYear; year++) {
            for (int month = 0; month < 12; month++) {
                for (int i = 0; i < opportunitiesPerMonth; i++) {

                    double amount = MIN_AMOUNT + random.nextDouble() * (MAX_AMOUNT - MIN_AMOUNT);
                    double probability = random.nextDouble() * 100.0;
                    Date creationDate = buildDate(month, year);
                    String color = "GREEN";
                    if (probability < 25) color = "RED";
                    else if (probability < 50) color = "GREY";
                    else if (probability < 75) color = "YELLOW";

                    builder.row(amount,
                            creationDate,
                            addDates(creationDate, (int) (AVG_CLOSING_DAYS + random.nextDouble() * AVG_CLOSING_DAYS * 0.5)),
                            randomValue(DIC_PIPELINE),
                            randomValue(DIC_STATUS),
                            randomValue(DIC_CUSTOMER),
                            randomValue(DIC_COUNTRIES),
                            randomValue(DIC_PRODUCT),
                            randomValue(DIC_SALES_PERSON),
                            probability,
                            randomValue(DIC_SOURCE),
                            amount * (1 + (random.nextDouble() * ((month*i)%10)/10)),
                            color);
                }
            }
        }*/
        //builder.row(1);
        return builder.buildDataSet();

    }
}

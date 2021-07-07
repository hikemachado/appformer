package org.uberfire.java.nio.fs.jgit.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class EnvProperties {

    private static Properties envProperties;

    public static Properties getInstace(){


        if(envProperties == null){

            InputStream stream = EnvProperties.class.getResourceAsStream(
                    "app.properties");
            envProperties = new Properties();

            try {
                envProperties.load(EnvProperties.class.getResourceAsStream("app.properties"));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        return envProperties;
    }

    protected EnvProperties(){

    }

    public static void main(String[] args){



        System.out.println("leu: " + EnvProperties.getInstace().getProperty("attributeBeing.basic.name"));
    }
}

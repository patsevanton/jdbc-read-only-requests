//package com.javapgsql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import java.util.regex.Pattern;  
import java.util.regex.Matcher; 

public class JavaPostgreSql {

    public static void main(String[] args) {

        String url = "jdbc:postgresql://localhost:5432/test";
        String user = "root";
        String password = "";

	Connection con = null;
	Boolean flgErr = false;

        try {

	    //DriverManager.registerDriver(new org.postgresql.Driver());
	    Class.forName("org.postgresql.Driver");
	    con = DriverManager.getConnection(url, user, password);
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT VERSION()");

            if (rs.next()) {
                System.out.println(rs.getString(1));
            }
	    stmt.close();
        }
        catch(java.lang.ClassNotFoundException e) {
	    System.err.print("ClassNotFoundException: ");
	    System.err.println(e.getMessage());
	    flgErr = true;
	}
        catch (SQLException ex) {
            Logger lgr = Logger.getLogger(JavaPostgreSql.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
	    flgErr = true;
        }

	try {
	    if( flgErr == true && con != null ) {
		con.close();
		return;
	    }
	}
	catch (SQLException ex) {
            Logger lgr = Logger.getLogger(JavaPostgreSql.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
	    flgErr = true;
        }

	int min = 1, max = 90000; // 900000
	int min2 = 1, max2 = 300;
        int rnd, rnd2;
	long startTime, endTime;
	double millis;
	String sql, sql2, s;
        Statement stmt;
	ResultSet rs;
        String str, k;
	String patt = "^(?:Planning|Execution)\\s+time:\\s+(.*?)\\s+ms"; // фильтр времени выполнения

	try {

//            stmt = con.createStatement();
//while(true) {
for(int i=0; i < 5; i++ ) { // заменить на while d htkbpt

            stmt = con.createStatement();

	    s = "";
	    sql = "";
	    sql2 = "";

	    rnd = ThreadLocalRandom.current().nextInt(min, max + 1);
//	    rnd = min + (int)(Math.random() * ((max - min) + 1)); // случайное число для java < 7
//*
//	    sql = "EXPLAIN ANALYSE select id1 from scale_data where id1=" + rnd; // release
	    sql = "explain analyze select id1 from scale_data where section = 1 and id1=" + rnd;

	    //startTime = System.currentTimeMillis();
            startTime = System.nanoTime();

            rs = stmt.executeQuery( sql );

	    // Время выполнения на клиенте
	    //endTime = System.currentTimeMillis();
            millis = (double)TimeUnit.NANOSECONDS.toNanos(System.nanoTime() - startTime) / 1_000_000.0;
            //millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

	    // получаем время выполнения на сервере
	    double t_1 = 0.0;
            while (rs.next()) {
		str = rs.getString(1);
		if( str.matches(patt) ) {
			t_1 += Double.parseDouble( str.replaceFirst(patt,"$1") );
		}
            }

	    s = "select: " + String.format("%.2f", millis) + " (" + String.format("%.2f", t_1) + ") ms";

//*/
//*
	// Update
//            stmt = con.createStatement();

	    rnd2 = ThreadLocalRandom.current().nextInt(min, max + 1);
	    //rnd2 = ThreadLocalRandom.current().nextInt(min2, max2 + 1);
 
	    //rnd2 = min + (int)(Math.random() * ((max - min) + 1));
	    //rnd2 = min2 + (int)(Math.random() * ((max2 - min2) + 1));

	    //sql2 = "EXPLAIN ANALYSE UPDATE scale_data SET id1 = " + rnd2 // release
            //            + " WHERE id1 = " + rnd;
	    sql2 = "EXPLAIN ANALYSE UPDATE scale_data SET id1 = " + rnd2
                        + " WHERE section = 1 and id1 = " + rnd;

	    //startTime = System.currentTimeMillis();
            startTime = System.nanoTime();

	    con.setAutoCommit(false);
            //stmt.executeUpdate( sql2 );
            rs = stmt.executeQuery( sql2 );
            con.commit();

	    //endTime = System.currentTimeMillis();
            millis = (double)TimeUnit.NANOSECONDS.toNanos(System.nanoTime() - startTime) / 1_000_000.0;
	    t_1 = 0.0;
            while (rs.next()) {
		str = rs.getString(1);
		if( str.matches(patt) ) {
			t_1 += Double.parseDouble( str.replaceFirst(patt,"$1") );
		}
            }

            //System.out.println("1: " + sql);
            //System.out.println("2: " + sql2);
	    s = "transact: " + String.format("%.2f", millis) + " (" + String.format("%.2f", t_1) + ") ms   " + s;
            System.out.println(s);

	    stmt.close();
//*/
} // for or while


	}
	catch (SQLException ex) {
            Logger lgr = Logger.getLogger(JavaPostgreSql.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
	    flgErr = true;
        }

	try {
	    if( flgErr == true && con != null ) {
		con.close();
	    }
	}
	catch (SQLException ex) {
            Logger lgr = Logger.getLogger(JavaPostgreSql.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
	    flgErr = true;
        }

    }
}

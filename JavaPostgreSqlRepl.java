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

public class JavaPostgreSqlRepl {

    public static void main(String[] args) {

	//jdbc:postgresql://node1,node2,node3/accounting?targetServerType=primary
        String url = "jdbc:postgresql://localhost:5000/test?targetServerType=primary";
        String user = "root";
        String password = "";

	//jdbc:postgresql://node1,node2,node3/accounting?targetServerType=preferSecondary&loadBalanceHosts=true
	// Здесь, судя по логике, должен быть второй сервер
        String url2 = "jdbc:postgresql://localhost:5002/test?targetServerType=preferSecondary&loadBalanceHosts=true";
        String user2 = "root";
        String password2 = "";

	Connection con = null, con2 = null;
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
	    con2 = DriverManager.getConnection(url2, user2, password2);
        }
        catch(java.lang.ClassNotFoundException e) {
	    System.err.print("ClassNotFoundException: ");
	    System.err.println(e.getMessage());
	    flgErr = true;
	}
        catch (SQLException ex) {
            Logger lgr = Logger.getLogger(JavaPostgreSqlRepl.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
	    flgErr = true;
        }

	try {
	    if( flgErr == true ) {
		if( con != null ) {
			con.close();
		}
		if( con2 != null ) {
			con2.close();
		}
		return;
	    }
	}
	catch (SQLException ex) {
            Logger lgr = Logger.getLogger(JavaPostgreSqlRepl.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
	    flgErr = true;
	    return;
        }

	int min = 1, max = 90000; // 900000
	int min2 = 1, max2 = 300;
        int rnd, rnd2;
	long startTime, endTime;
	double millis;
	String sql, sql2, s;
        Statement stmt, stmt2;
	ResultSet rs;
        String str, k;
	String patt = "^(?:Planning|Execution)\\s+time:\\s+(.*?)\\s+ms"; // фильтр времени выполнения

	try {

//            stmt = con.createStatement();
//while(true) {
for(int i=0; i < 5; i++ ) { // заменить на while в релизе

            stmt = con.createStatement();
            stmt2 = con2.createStatement();

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

            rs = stmt2.executeQuery( sql );

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
	    stmt2.close();
//*/
} // for or while


	}
	catch (SQLException ex) {
            Logger lgr = Logger.getLogger(JavaPostgreSqlRepl.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
	    flgErr = true;
        }

	try {
	    if( flgErr == true ) {
		con.close();
		con2.close();
	    }
	}
	catch (SQLException ex) {
            Logger lgr = Logger.getLogger(JavaPostgreSqlRepl.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
	    flgErr = true;
        }

    }
}

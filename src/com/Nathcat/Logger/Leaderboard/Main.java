package com.Nathcat.Logger.Leaderboard;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.Objects;

public class Main {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/submitScore", new ScoreSubmitter());
        server.createContext("/getTop5", new GetTopScores());
        server.setExecutor(null);
        server.start();
    }

    private static class ScoreSubmitter implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println(t.getRequestMethod());
            if (t.getRequestMethod().contentEquals("OPTIONS")) {
                System.out.println("ScoreSubmitter: Got CORS preflight responding.");
                Headers h = t.getResponseHeaders();
                h.add("Access-Control-Allow-Origin", "*");
                h.add("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
                h.add("Access-Control-Allow-Credentials", "true");
                h.add("Access-Control-Allow-Headers", "Accept, Content-Type, X-Access-Token, X-Application-Name, X-Request-Sent-Time");
                h.add("Access-Control-Max-Age", "86400");
                t.sendResponseHeaders(200, -1);
                return;
            }

            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            InputStream in = t.getRequestBody();
            String s = new String(in.readAllBytes());

            System.out.println("ScoreSubmitter: Got body " + s);
            
            JSONObject body;
            try {
                JSONParser parser = new JSONParser();
                body = (JSONObject) parser.parse(s);
            } catch (ParseException e) {
                OutputStream os = t.getResponseBody();
                String response = "Invalid JSON in request body";
                t.sendResponseHeaders(400, response.length());
                os.write(response.getBytes());
                os.flush(); os.close();

                System.out.println("ScoreSubmitter: Found invalid JSON in body!");

                return;
            }

            if (!body.containsKey("username") || !body.containsKey("score")) {
                OutputStream os = t.getResponseBody();
                String response = "JSON supplied does not contain either username or score field";
                os.write(response.getBytes());
                os.flush();
                os.close();
                t.sendResponseHeaders(400, response.length());
            }
            else {
                String username = (String) body.get("username");
                String score = (String) body.get("score");

                try {
                    Connection db = DriverManager.getConnection("jdbc:sqlite:Assets/leaderboard.db");
                    Statement stmt = db.createStatement();
                    stmt.executeUpdate("create table if not exists leaderboard (username varchar(255) primary key, score int)");
                    stmt.close();
                    PreparedStatement pstmt = db.prepareStatement("insert into leaderboard (username, score) values (?, ?) on conflict(username) do update set score= ? ");
                    pstmt.setString(1, username);
                    pstmt.setInt(2, Integer.parseInt(score));
                    pstmt.setInt(3, Integer.parseInt(score));
                    pstmt.executeUpdate();
                    pstmt.close();

                    t.sendResponseHeaders(200, "done".length());
                    OutputStream os = t.getResponseBody();
                    os.write("done".getBytes());
                    os.flush(); os.close();
                } catch (Exception e) {
                    OutputStream os = t.getResponseBody();
                    String response = e.getMessage() + "\n\n" + e.getStackTrace();
                    t.sendResponseHeaders(500, response.length());
                    os.write(response.getBytes());
                    os.flush(); os.close();
                }
            }
        }
    }

    private static class GetTopScores implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            InputStream in = t.getRequestBody();
            String s = new String(in.readAllBytes());

            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            try {
                Connection db = DriverManager.getConnection("jdbc:sqlite:Assets/leaderboard.db");
                Statement stmt = db.createStatement();
                stmt.executeUpdate("create table if not exists leaderboard (username varchar(255) primary key, score int)");
                ResultSet rs = stmt.executeQuery("select * from leaderboard order by score desc limit 5");
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append(rs.getString("username") + " - " + rs.getInt("score") + "\n");
                }
                stmt.close();

                String response = sb.toString();
                OutputStream os = t.getResponseBody();
                t.sendResponseHeaders(200, response.length());
                os.write(response.getBytes());
                os.flush(); os.close();
            } catch (SQLException e) {
                OutputStream os = t.getResponseBody();
                String response = e.getMessage() + "\n\n" + e.getStackTrace();
                t.sendResponseHeaders(500, response.length());
                os.write(response.getBytes());
                os.flush(); os.close();
            }
        }
    }
}

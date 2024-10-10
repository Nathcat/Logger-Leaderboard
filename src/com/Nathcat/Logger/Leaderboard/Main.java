package com.Nathcat.Logger.Leaderboard;

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
            InputStream in = t.getRequestBody();
            String s = new String(in.readAllBytes());
            in.close();

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

                return;
            }

            if (!body.containsKey("username") || !body.containsKey("score")) {
                OutputStream os = t.getResponseBody();
                String response = "JSON supplied does not contain either username or score field";
                os.write(response.getBytes());
                os.flush();
                os.close();
                t.sendResponseHeaders(400, response.length());
                return;
            }
            else {
                String username = (String) body.get("username");
                int score = (int) body.get("score");

                try {
                    Connection db = DriverManager.getConnection("jdbc:sqlite:Assets/leaderboard.db");
                    Statement stmt = db.createStatement();
                    stmt.executeUpdate("if not exists create table leaderboard (username varchar(255), score int)");
                    stmt.executeUpdate("insert into leaderboard (username, score) values ('" + username + "', " + score + ")");
                    stmt.close();
                    t.sendResponseHeaders(200, 0);
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

    private static class GetTopScores implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            InputStream in = t.getRequestBody();
            String s = new String(in.readAllBytes());

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
                return;
            }

            try {
                Connection db = DriverManager.getConnection("jdbc:sqlite:Assets/leaderboard.db");
                Statement stmt = db.createStatement();
                stmt.executeQuery("select * from leaderboard order by desc limit 5");
                StringBuilder sb = new StringBuilder();
                ResultSet rs = stmt.getResultSet();
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

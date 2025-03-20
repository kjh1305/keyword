package com.example.demo.common.util;

import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

//API데이터 읽어오기 기능 중복제거
public class ApiDataUtil {

    private ApiDataUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    public static String getApiData(HttpURLConnection httpURLConnection) throws IOException{

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        httpURLConnection.getResponseCode() == HttpStatus.OK.value()
                                ? httpURLConnection.getInputStream()
                                : httpURLConnection.getErrorStream(),
                        "UTF-8"
                )
        )) {
            // 응답 데이터를 StringBuilder로 읽기
            StringBuilder result = new StringBuilder();
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                result.append(inputLine);
            }
            return result.toString();
        }
    }
}

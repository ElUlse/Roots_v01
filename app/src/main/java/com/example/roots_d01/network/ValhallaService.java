package com.example.roots_d01.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Url;

// This interface defines the API endpoints we will call using Retrofit
public interface ValhallaService {

    /**
     * Sends a map matching request to the specified Valhalla endpoint URL.
     * Uses the trace_attributes action.
     *
     * @param url The full URL of the Valhalla endpoint (e.g., "https://valhalla1.openstreetmap.de/trace_attributes").
     * @param request The request body containing the shape points and options.
     * @return A Retrofit Call object which can be executed to get the ValhallaResponse.
     */
    @POST // We specify the HTTP method here. The actual path is part of the @Url.
    Call<ValhallaResponse> getTraceAttributes(
            @Url String url,          // @Url tells Retrofit to use this string as the full request URL
            @Body ValhallaRequest request // @Body tells Retrofit to serialize this object as the JSON request body
    );

    // If you wanted to use trace_route later, you could add another method:
    // @POST
    // Call<YourTraceRouteResponseClass> getTraceRoute(@Url String url, @Body ValhallaRequest request);
    // (You would need to define YourTraceRouteResponseClass similar to ValhallaResponse)
}
package com.mainstreetcode.teammates.rest;

import com.google.gson.JsonObject;
import com.mainstreetcode.teammates.model.JoinRequest;
import com.mainstreetcode.teammates.model.Team;
import com.mainstreetcode.teammates.model.User;

import java.util.List;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * RESTful client implementation
 * <p>
 * Created by Shemanigans on 6/12/17.
 */

public interface TeammateApi {
    @POST("api/signUp")
    Observable<User> signUp(@Body User user);

    @POST("api/signIn")
    Observable<User> signIn(@Body JsonObject request);

    @GET("api/me")
    Observable<User> getMe();

    @GET("api/signOut")
    Observable<JsonObject> signOut();

    @GET("api/teams")
    Observable<List<Team>> findTeam(@Query("name") String teamName);

    @GET("api/me/teams")
    Observable<List<Team>> getMyTeams();

    @GET("api/teams/{id}/join")
    Observable<JoinRequest> joinTeam(@Path("id") String teamId, @Query("role") String role);

    @POST("api/teams")
    Observable<Team> createTeam(@Body Team team);
}

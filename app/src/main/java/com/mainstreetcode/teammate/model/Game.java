package com.mainstreetcode.teammate.model;

import android.arch.persistence.room.Ignore;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mainstreetcode.teammate.R;
import com.mainstreetcode.teammate.model.enums.Sport;
import com.mainstreetcode.teammate.persistence.entity.GameEntity;
import com.mainstreetcode.teammate.util.IdCache;
import com.mainstreetcode.teammate.util.ModelUtils;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.mainstreetcode.teammate.util.ModelUtils.EMPTY_STRING;
import static com.mainstreetcode.teammate.util.ModelUtils.areNotEmpty;

/**
 * Event events
 */

public class Game extends GameEntity
        implements
        Model<Game>,
        HeaderedModel<Game>,
        ListableModel<Game> {

    @Ignore private static final IdCache holder = IdCache.cache(4);

    public Game(@NonNull String id, String refPath, String score,
                Date created, Sport sport, Event event, Tournament tournament,
                Competitor home, Competitor away, Competitor winner,
                int seed, int leg, int round, int homeScore, int awayScore,
                boolean ended, boolean canDraw) {
        super(id, refPath, score, created, sport, event, tournament, home, away, winner, seed, leg, round, homeScore, awayScore, ended, canDraw);
    }

    protected Game(Parcel in) {
        super(in);
    }

    @Override
    public List<Item<Game>> asItems() {
        return Arrays.asList(
                Item.number(holder.get(0), 0, Item.INPUT, R.string.game_home_score, () -> String.valueOf(homeScore), this::setHomeScore, this),
                Item.number(holder.get(1), 1, Item.INPUT, R.string.game_away_score, () -> String.valueOf(awayScore), this::setAwayScore, this),
                Item.number(holder.get(2), 1, Item.NUMBER, R.string.game_round, () -> String.valueOf(round), ignored -> {}, this),
                Item.number(holder.get(3), 1, Item.NUMBER, R.string.game_leg, () -> String.valueOf(leg), ignored -> {}, this)
        );
    }

    @Override
    public Item<Game> getHeaderItem() {
        return Item.text(EMPTY_STRING, 0, Item.IMAGE, R.string.team_logo, () -> "", url -> {}, this);
    }

    @Override
    public boolean areContentsTheSame(Identifiable other) {
        if (!(other instanceof Game)) return id.equals(other.getId());
        Game casted = (Game) other;
        return score.equals(casted.score)
                && home.areContentsTheSame(casted.home)
                && away.areContentsTheSame(casted.away);
    }

    @Override
    public boolean hasMajorFields() {
        return areNotEmpty(id, refPath, score) && home.hasMajorFields() && away.hasMajorFields();
    }

    @Override
    public Object getChangePayload(Identifiable other) {
        return other;
    }

    @Override
    public boolean isEmpty() {
        return TextUtils.isEmpty(id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void update(Game updatedGame) {
        this.id = updatedGame.id;
        this.score = updatedGame.score;
        this.created = updatedGame.created;
        this.seed = updatedGame.seed;
        this.round = updatedGame.round;
        this.leg = updatedGame.leg;
        this.ended = updatedGame.ended;
        this.canDraw = updatedGame.canDraw;
        this.sport.update(updatedGame.sport);
        if (updatedGame.tournament.hasMajorFields())
            this.tournament.update(updatedGame.tournament);
        if (updatedGame.home.hasMajorFields() && this.home.hasSameType(updatedGame.home))
            this.home.update(updatedGame.home);
        else this.home = updatedGame.home;
        if (updatedGame.away.hasMajorFields() && this.away.hasSameType(updatedGame.away))
            this.away.update(updatedGame.away);
        else this.away = updatedGame.away;
        if (updatedGame.winner.hasMajorFields() && this.winner.hasSameType(updatedGame.winner))
            this.winner.update(updatedGame.winner);
        else this.winner = updatedGame.winner;
        if (updatedGame.event.hasMajorFields()) this.event.update(updatedGame.event);
    }

    @Override
    public int compareTo(@NonNull Game o) {
        int createdComparison = created.compareTo(o.created);

        return createdComparison != 0 ? createdComparison : id.compareTo(o.id);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Game> CREATOR = new Creator<Game>() {
        @Override
        public Game createFromParcel(Parcel in) {
            return new Game(in);
        }

        @Override
        public Game[] newArray(int size) {
            return new Game[size];
        }
    };

    public static class GsonAdapter
            implements
            JsonSerializer<Game>,
            JsonDeserializer<Game> {

        private static final String ID_KEY = "_id";
        private static final String REF_PATH = "refPath";
        private static final String SCORE = "score";
        private static final String CREATED_KEY = "created";
        private static final String SPORT_KEY = "sport";
        private static final String EVENT = "event";
        private static final String TOURNAMENT = "tournament";
        private static final String HOME = "home";
        private static final String AWAY = "away";
        private static final String WINNER = "winner";
        private static final String LEG = "leg";
        private static final String SEED = "seed";
        private static final String ROUND = "round";
        private static final String HOME_SCORE = "homeScore";
        private static final String AWAY_SCORE = "awayScore";
        private static final String ENDED = "ended";
        private static final String CAN_DRAW = "canDraw";

        @Override
        public JsonElement serialize(Game src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject body = new JsonObject();
            body.addProperty(ENDED, src.ended);
            if (!src.event.isEmpty()) body.addProperty(EVENT, src.event.getId());
            return body;
        }

        @Override
        public Game deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return new Game(json.getAsString(), "", "TBD", new Date(), Sport.empty(), Event.empty(),
                        Tournament.empty(Team.empty()), Competitor.empty(), Competitor.empty(), Competitor.empty(),
                        0, 0, 0, 0, 0, false, false);
            }

            JsonObject body = json.getAsJsonObject();

            String id = ModelUtils.asString(ID_KEY, body);
            String refPath = ModelUtils.asString(REF_PATH, body);
            String score = ModelUtils.asString(SCORE, body);
            String created = ModelUtils.asString(CREATED_KEY, body);
            String sportCode = ModelUtils.asString(SPORT_KEY, body);

            int seed = (int) ModelUtils.asFloat(SEED, body);
            int leg = (int) ModelUtils.asFloat(LEG, body);
            int round = (int) ModelUtils.asFloat(ROUND, body);
            int homeScore = (int) ModelUtils.asFloat(HOME_SCORE, body);
            int awayScore = (int) ModelUtils.asFloat(AWAY_SCORE, body);
            boolean ended = ModelUtils.asBoolean(ENDED, body);
            boolean canDraw = ModelUtils.asBoolean(CAN_DRAW, body);

            Sport sport = Config.sportFromCode(sportCode);
            Event event = context.deserialize(body.get(EVENT), Event.class);
            Tournament tournament = context.deserialize(body.get(TOURNAMENT), Tournament.class);
            Competitor home = context.deserialize(body.get(HOME), Competitor.class);
            Competitor away = context.deserialize(body.get(AWAY), Competitor.class);
            Competitor winner = body.has(WINNER) ? context.deserialize(body.get(WINNER), Competitor.class) : Competitor.empty();

            if (event == null) event = Event.empty();

            return new Game(id, refPath, score, ModelUtils.parseDate(created), sport,
                    event, tournament, home, away, winner, seed, leg, round, homeScore, awayScore, ended, canDraw);
        }
    }
}

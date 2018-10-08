package com.mainstreetcode.teammate.viewmodel.gofers;

import android.annotation.SuppressLint;
import android.arch.core.util.Function;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.util.DiffUtil;

import com.mainstreetcode.teammate.R;
import com.mainstreetcode.teammate.model.Competitor;
import com.mainstreetcode.teammate.model.Identifiable;
import com.mainstreetcode.teammate.model.Tournament;
import com.mainstreetcode.teammate.util.ModelUtils;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;

public class TournamentGofer extends TeamHostingGofer<Tournament> {

    private static final int CREATING = 0;
    private static final int EDITING = 1;

    private int state;
    private final Function<Tournament, Flowable<Tournament>> getFunction;
    private final Function<Tournament, Single<Tournament>> deleteFunction;
    private final Function<Tournament, Single<Tournament>> updateFunction;
    private final Function<Tournament, Flowable<List<Competitor>>> competitorsFunction;

    @SuppressLint("CheckResult")
    public TournamentGofer(Tournament model,
                           Consumer<Throwable> onError,
                           Function<Tournament, Flowable<Tournament>> getFunction,
                           Function<Tournament, Single<Tournament>> upsertFunction,
                           Function<Tournament, Single<Tournament>> deleteFunction,
                           Function<Tournament, Flowable<List<Competitor>>> competitorsFunction) {
        super(model, onError);
        this.getFunction = getFunction;
        this.updateFunction = upsertFunction;
        this.deleteFunction = deleteFunction;
        this.competitorsFunction = competitorsFunction;

        items.addAll(model.asItems());
        state = model.isEmpty() ? CREATING : EDITING;
    }

    public boolean canEditBeforeCreation() {
        return canEditAfterCreation() && model.isEmpty();
    }

    public boolean canEditAfterCreation() {
        return state == CREATING || hasPrivilegedRole();
    }

    public String getToolbarTitle(Fragment fragment) {
        return model.isEmpty()
                ? fragment.getString(R.string.create_tournament)
                : fragment.getString(R.string.edit_tournament, model.getName());
    }

    @Nullable
    @Override
    public String getImageClickMessage(Fragment fragment) {
        if (state == CREATING) return fragment.getString(R.string.create_tournament_first);
        else if (!hasPrivilegedRole()) return fragment.getString(R.string.no_permission);
        return null;
    }

    @Override
    Flowable<DiffUtil.DiffResult> fetch() {
        Flowable<List<Identifiable>> eventFlowable = getFunction.apply(model).map(Tournament::asIdentifiables);
        Flowable<List<Identifiable>> competitorsFlowable = competitorsFunction.apply(model).map(ModelUtils::asIdentifiables);
        Flowable<List<Identifiable>> sourceFlowable = Flowable.mergeDelayError(eventFlowable, competitorsFlowable);
        return Identifiable.diff(sourceFlowable, this::getItems, this::preserveItems);
    }

    Single<DiffUtil.DiffResult> upsert() {
        Single<List<Identifiable>> source = updateFunction.apply(model).map(Tournament::asIdentifiables);
        return Identifiable.diff(source, this::getItems, this::preserveItems);
    }

    Completable delete() {
        return deleteFunction.apply(model).toCompletable();
    }
}
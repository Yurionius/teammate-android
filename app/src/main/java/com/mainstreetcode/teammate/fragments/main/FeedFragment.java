package com.mainstreetcode.teammate.fragments.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.mainstreetcode.teammate.R;
import com.mainstreetcode.teammate.adapters.FeedAdapter;
import com.mainstreetcode.teammate.adapters.viewholders.ChoiceBar;
import com.mainstreetcode.teammate.adapters.viewholders.EmptyViewHolder;
import com.mainstreetcode.teammate.adapters.viewholders.FeedItemViewHolder;
import com.mainstreetcode.teammate.baseclasses.MainActivityFragment;
import com.mainstreetcode.teammate.model.Competitor;
import com.mainstreetcode.teammate.model.Event;
import com.mainstreetcode.teammate.model.JoinRequest;
import com.mainstreetcode.teammate.model.ListState;
import com.mainstreetcode.teammate.model.Media;
import com.mainstreetcode.teammate.model.Model;
import com.mainstreetcode.teammate.model.User;
import com.mainstreetcode.teammate.notifications.FeedItem;
import com.mainstreetcode.teammate.util.ScrollManager;
import com.tunjid.androidbootstrap.core.abstractclasses.BaseFragment;
import com.tunjid.androidbootstrap.recyclerview.InteractiveViewHolder;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Single;

import static com.google.android.material.snackbar.Snackbar.Callback.DISMISS_EVENT_MANUAL;
import static com.google.android.material.snackbar.Snackbar.Callback.DISMISS_EVENT_SWIPE;
import static com.mainstreetcode.teammate.util.ViewHolderUtil.getTransitionName;

/**
 * Home screen
 */

public final class FeedFragment extends MainActivityFragment
        implements FeedAdapter.FeedItemAdapterListener {

    private int onBoardingIndex;
    private boolean isOnBoarding;
    private AtomicBoolean bottomBarState;

    public static FeedFragment newInstance() {
        FeedFragment fragment = new FeedFragment();
        Bundle args = new Bundle();

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        bottomBarState = new AtomicBoolean(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_list_with_refresh, container, false);

        Runnable refreshAction = () -> disposables.add(feedViewModel.refresh(FeedItem.class).subscribe(this::onFeedUpdated, defaultErrorHandler));

        scrollManager = ScrollManager.<InteractiveViewHolder>with(rootView.findViewById(R.id.list_layout))
                .withPlaceholder(new EmptyViewHolder(rootView, R.drawable.ic_notifications_white_24dp, R.string.no_feed))
                .withAdapter(new FeedAdapter(feedViewModel.getModelList(FeedItem.class), this))
                .withRefreshLayout(rootView.findViewById(R.id.refresh_layout), refreshAction)
                .addScrollListener((dx, dy) -> updateTopSpacerElevation())
                .withInconsistencyHandler(this::onInconsistencyDetected)
                .withLinearLayoutManager()
                .build();

        bottomBarState.set(true);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setToolbarTitle(userViewModel.getCurrentUser());
    }

    @Override
    public void onResume() {
        super.onResume();
        scrollManager.setRefreshing();
        disposables.add(feedViewModel.refresh(FeedItem.class).subscribe(this::onFeedUpdated, defaultErrorHandler));
    }

    @Override
    public void togglePersistentUi() {
        super.togglePersistentUi();
        onBoard();
        setToolbarTitle(userViewModel.getCurrentUser());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_feed, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onFeedItemClicked(FeedItem item) {
        Model model = item.getModel();
        Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        if (model instanceof Event) {
            builder.setTitle(getString(R.string.attend_event))
                    .setPositiveButton(R.string.yes, (dialog, which) -> onFeedItemAction(feedViewModel.rsvpEvent(item, true)))
                    .setNegativeButton(R.string.no, (dialog, which) -> onFeedItemAction(feedViewModel.rsvpEvent(item, false)))
                    .setNeutralButton(R.string.event_details, ((dialog, which) -> showFragment(EventEditFragment.newInstance((Event) model))))
                    .show();
        }
        if (model instanceof Competitor) {
            builder.setTitle(getString(R.string.accept_competition))
                    .setPositiveButton(R.string.yes, (dialog, which) -> onFeedItemAction(feedViewModel.processCompetitor(item, true)))
                    .setNegativeButton(R.string.no, (dialog, which) -> onFeedItemAction(feedViewModel.processCompetitor(item, false)))
                    .setNeutralButton(R.string.event_details, ((dialog, which) -> {
                        Competitor competitor = (Competitor) model;
                        BaseFragment fragment = !competitor.getGame().isEmpty()
                                ? GameFragment.newInstance(competitor.getGame())
                                : !competitor.getTournament().isEmpty()
                                ? TournamentDetailFragment.newInstance(competitor.getTournament()).pending(competitor)
                                : null;
                        if (fragment != null) showFragment(fragment);
                    }))
                    .show();
        }
        else if (model instanceof JoinRequest) {
            JoinRequest request = ((JoinRequest) model);
            String title = userViewModel.getCurrentUser().equals(request.getUser()) && request.isUserApproved()
                    ? getString(R.string.clarify_invitation, request.getTeam().getName())
                    : request.isTeamApproved()
                    ? getString(R.string.accept_invitation, request.getTeam().getName())
                    : getString(R.string.add_user_to_team, request.getUser().getFirstName());

            builder.setTitle(title)
                    .setPositiveButton(R.string.yes, (dialog, which) -> onFeedItemAction(feedViewModel.processJoinRequest(item, true)))
                    .setNegativeButton(R.string.no, (dialog, which) -> onFeedItemAction(feedViewModel.processJoinRequest(item, false)))
                    .setNeutralButton(R.string.event_details, ((dialog, which) -> showFragment(JoinRequestFragment.viewInstance(request))))
                    .show();
        }
        else if (model instanceof Media) {
            bottomBarState.set(false);
            toggleBottombar(false);
            showFragment(MediaDetailFragment.newInstance((Media) model));
        }
    }

    @Override
    public boolean showsFab() {return false;}

    @Override
    public boolean showsBottomNav() {return bottomBarState.get();}

    @Override
    @Nullable
    @SuppressLint("CommitTransaction")
    @SuppressWarnings("ConstantConditions")
    public FragmentTransaction provideFragmentTransaction(BaseFragment fragmentTo) {
        if (fragmentTo.getStableTag().contains(MediaDetailFragment.class.getSimpleName())) {
            Media media = fragmentTo.getArguments().getParcelable(MediaDetailFragment.ARG_MEDIA);

            if (media == null) return null;
            FeedItemViewHolder holder = (FeedItemViewHolder) scrollManager.findViewHolderForItemId(media.hashCode());
            if (holder == null) return null;

            return beginTransaction()
                    .addSharedElement(holder.itemView, getTransitionName(media, R.id.fragment_media_background))
                    .addSharedElement(holder.thumbnail, getTransitionName(media, R.id.fragment_media_thumbnail));
        }
        else if (fragmentTo.getStableTag().contains(JoinRequestFragment.class.getSimpleName())) {
            JoinRequest request = fragmentTo.getArguments().getParcelable(JoinRequestFragment.ARG_JOIN_REQUEST);
            if (request == null) return null;

            FeedItemViewHolder holder = (FeedItemViewHolder) scrollManager.findViewHolderForItemId(request.hashCode());
            if (holder == null) return null;

            return beginTransaction()
                    .addSharedElement(holder.itemView, getTransitionName(request, R.id.fragment_header_background))
                    .addSharedElement(holder.thumbnail, getTransitionName(request, R.id.fragment_header_thumbnail));
        }
        else if (fragmentTo.getStableTag().contains(EventEditFragment.class.getSimpleName())) {
            Bundle args = fragmentTo.getArguments();
            if (args == null) return super.provideFragmentTransaction(fragmentTo);

            Event event = args.getParcelable(EventEditFragment.ARG_EVENT);
            if (event == null) return super.provideFragmentTransaction(fragmentTo);

            FeedItemViewHolder holder = (FeedItemViewHolder) scrollManager.findViewHolderForItemId(event.hashCode());
            if (holder == null) return super.provideFragmentTransaction(fragmentTo);

            return beginTransaction()
                    .addSharedElement(holder.itemView, getTransitionName(event, R.id.fragment_header_background))
                    .addSharedElement(holder.thumbnail, getTransitionName(event, R.id.fragment_header_thumbnail));

        }
        return super.provideFragmentTransaction(fragmentTo);
    }

    private void onFeedItemAction(Single<DiffUtil.DiffResult> diffResultSingle) {
        toggleProgress(true);
        disposables.add(diffResultSingle.subscribe(this::onFeedUpdated, defaultErrorHandler));
    }

    private void onFeedUpdated(DiffUtil.DiffResult diffResult) {
        toggleProgress(false);
        boolean isOnATeam = teamViewModel.isOnATeam();
        scrollManager.onDiff(diffResult);
        feedViewModel.clearNotifications(FeedItem.class);
        scrollManager.updateForEmptyList(ListState.of(
                isOnATeam ? R.drawable.ic_notifications_white_24dp : R.drawable.ic_group_black_24dp,
                isOnATeam ? R.string.no_feed : R.string.no_team_feed));
    }

    private void setToolbarTitle(User user) {
        setToolbarTitle(getString(R.string.home_greeting, getTimeOfDay(), user.getFirstName()));
    }

    private void onBoard() {
        if (isOnBoarding || prefsViewModel.isOnBoarded() || isBottomSheetShowing()) return;
        List<String> prompts = Arrays.asList(getResources().getStringArray(R.array.on_boarding));
        prompts = prompts.subList(onBoardingIndex, prompts.size());

        isOnBoarding = true;
        Iterator<String> iterator = prompts.iterator();
        AtomicReference<Runnable> ref = new AtomicReference<>();

        ref.set(() -> showChoices(choiceBar -> choiceBar.setText(iterator.next())
                .setPositiveText(getString(iterator.hasNext() ? R.string.next : R.string.finish))
                .setPositiveClickListener(view -> {
                    onBoardingIndex++;
                    if (iterator.hasNext()) ref.get().run();
                    else choiceBar.dismiss();
                })
                .addCallback(new BaseTransientBottomBar.BaseCallback<ChoiceBar>() {
                    public void onDismissed(ChoiceBar bar, int event) {onBoardDismissed(event); }
                })
        ));
        ref.get().run();
    }

    private void onBoardDismissed(int event) {
        isOnBoarding = false;
        if (event != DISMISS_EVENT_SWIPE && event != DISMISS_EVENT_MANUAL) return;
        onBoardingIndex = 0;
        prefsViewModel.setOnBoarded(true);
    }

    private static String getTimeOfDay() {
        int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hourOfDay > 0 && hourOfDay < 12) return "morning";
        else return "evening";
    }
}

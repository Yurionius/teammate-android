/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.mainstreetcode.teammate.fragments.main

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils.isEmpty
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.viewpager.widget.ViewPager.SCROLL_STATE_DRAGGING
import com.google.android.material.chip.Chip
import com.mainstreetcode.teammate.R
import com.mainstreetcode.teammate.adapters.Shell
import com.mainstreetcode.teammate.adapters.chatAdapter
import com.mainstreetcode.teammate.adapters.viewholders.EmptyViewHolder
import com.mainstreetcode.teammate.adapters.viewholders.TeamChatViewHolder
import com.mainstreetcode.teammate.baseclasses.TeammatesBaseFragment
import com.mainstreetcode.teammate.databinding.FragmentChatBinding
import com.mainstreetcode.teammate.model.Chat
import com.mainstreetcode.teammate.model.Team
import com.mainstreetcode.teammate.util.Deferrer
import com.mainstreetcode.teammate.util.ErrorHandler
import com.mainstreetcode.teammate.util.ScrollManager
import com.mainstreetcode.teammate.util.setMaterialOverlay
import com.mainstreetcode.teammate.viewmodel.swap
import com.tunjid.androidx.core.components.args
import com.tunjid.androidx.recyclerview.diff.Differentiable
import com.tunjid.androidx.view.animator.ViewHider
import io.reactivex.disposables.Disposable
import kotlin.math.abs

class ChatFragment : TeammatesBaseFragment(R.layout.fragment_chat),
        TextView.OnEditorActionListener,
        Shell.TeamAdapterListener {

    private var wasScrolling: Boolean = false
    private var unreadCount: Int = 0

    private var team by args<Team>()

    private val items: MutableList<Differentiable>
        get() = chatViewModel.getModelList(team)

    private lateinit var chatDisposable: Disposable

    private var swappedTeam = false
    private var deferrer: Deferrer? = null
    private var dateHider: ViewHider<Chip>? = null
    private var newMessageHider: ViewHider<Chip>? = null

    private val isSubscribedToChat: Boolean get() = ::chatDisposable.isInitialized && !chatDisposable.isDisposed

    private val isNearBottomOfChat: Boolean get() = abs(items.size - scrollManager.lastVisiblePosition) < 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        defaultErrorHandler.addAction { swappedTeam = false }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = FragmentChatBinding.bind(view).run {
        super.onViewCreated(view, savedInstanceState)
        defaultUi(
                toolbarTitle = getString(R.string.team_chat_title, team.name),
                toolBarMenu = R.menu.fragment_chat,
                fabShows = showsFab
        )

        footerBackground.setMaterialOverlay()

        dateHider = ViewHider.of(date).setDirection(ViewHider.TOP).build()
        newMessageHider = ViewHider.of(newMessages).setDirection(ViewHider.BOTTOM).build()

        deferrer = Deferrer(2000) { dateHider?.hide() }

        scrollManager = ScrollManager.with<TeamChatViewHolder>(view.findViewById(R.id.chat))
                .withPlaceholder(EmptyViewHolder(view, R.drawable.ic_message_black_24dp, R.string.no_chats))
                .onLayoutManager { layoutManager -> (layoutManager as LinearLayoutManager).stackFromEnd = true }
                .withAdapter(chatAdapter(::items, userViewModel.currentUser, this@ChatFragment::onChatClicked))
                .withEndlessScroll { fetchChatsBefore(false) }
                .withRefreshLayout(refreshLayout) { refreshLayout.isRefreshing = false }
                .addScrollListener { _, _ -> updateTopSpacerElevation() }
                .withInconsistencyHandler(this@ChatFragment::onInconsistencyDetected)
                .addStateListener(this@ChatFragment::onScrollStateChanged)
                .addScrollListener(this@ChatFragment::onScroll)
                .withLinearLayoutManager()
                .build()

        newMessageHider?.view?.setOnClickListener { scrollManager.withRecyclerView { rv -> rv.smoothScrollToPosition(items.size - 1) } }
        dateHider?.view?.setOnClickListener { dateHider?.hide() }
        send.setOnClickListener { sendChat(input) }
        input.setOnEditorActionListener(this@ChatFragment)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                wasScrolling = false
            }
        })

        newMessageHider?.hide()
        dateHider?.hide()

        Unit
    }

    override fun onResume() {
        super.onResume()

        subscribeToChat()

        if (teamViewModel.defaultTeam != team) onTeamClicked(teamViewModel.defaultTeam)
        else fetchChatsBefore(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_pick_team -> bottomSheetDriver.showBottomSheet(
                requestCode = R.id.request_chat_team_pick,
                title = getString(R.string.pick_team),
                fragment = TeamsFragment.newInstance()
        ).let { true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatViewModel.updateLastSeen(team)
        newMessageHider = null
        dateHider = null
        deferrer = null
    }

    override fun onTeamClicked(item: Team) = disposables.add(teamViewModel.swap(team, item, chatViewModel) {
        swappedTeam = true
        disposables.clear()
        bottomSheetDriver.hideBottomSheet()

        subscribeToChat()
        updateUi(toolbarTitle = getString(R.string.team_chat_title, team.name))
    }.subscribe(::onChatsUpdated, defaultErrorHandler::invoke) { swappedTeam = false }).let { Unit }

    override fun onEditorAction(textView: TextView, actionId: Int, event: KeyEvent): Boolean = when {
        actionId == EditorInfo.IME_ACTION_DONE || event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN -> sendChat(textView).let { true }
        else -> false
    }

    private fun onChatClicked(chat: Chat) {
        if (!chat.isEmpty) return

        val index = items.indexOf(chat)
        if (index == -1) return

        items.removeAt(index)
        scrollManager.notifyItemRemoved(index)

        postChat(chat)
    }

    private fun fetchChatsBefore(fetchLatest: Boolean) {
        scrollManager.setRefreshing()
        disposables.add(chatViewModel.getMany(team, fetchLatest).subscribe(this::onChatsUpdated, defaultErrorHandler::invoke))
    }

    private fun subscribeToChat() {
        chatDisposable = chatViewModel.listenForChat(team).subscribe({ chat ->
            items.add(chat)
            val nearBottomOfChat = isNearBottomOfChat
            unreadCount = if (nearBottomOfChat) 0 else unreadCount + 1
            notifyAndScrollToLast(nearBottomOfChat)
            updateUnreadCount()
        }, ErrorHandler.builder()
                .defaultMessage(getString(R.string.error_default))
                .add { message -> transientBarDriver.showSnackBar(message.message) }
                .build()::invoke)

        disposables.add(chatDisposable)
    }

    private fun sendChat(textView: TextView) {
        val text = textView.text.toString()
        textView.text = null

        if (isEmpty(text)) return

        val chat = Chat.chat(text, userViewModel.currentUser, team)
        items.add(chat)

        wasScrolling = false
        notifyAndScrollToLast(true)
        postChat(chat)
    }

    private fun postChat(chat: Chat) {
        disposables.add(chatViewModel.post(chat).subscribe({
            chatViewModel.updateLastSeen(team)
            val index = items.indexOf(chat)

            if (index != -1) scrollManager.notifyItemChanged(index)
            if (!isSubscribedToChat) subscribeToChat()
        }, ErrorHandler.builder()
                .defaultMessage(getString(R.string.error_default))
                .add {
                    val index = items.indexOf(chat)
                    if (index != -1) scrollManager.notifyItemChanged(index)
                }
                .build()::invoke))
    }

    private fun notifyAndScrollToLast(scrollToLast: Boolean) {
        val index = items.size - 1

        val recyclerView = scrollManager.recyclerView ?: return

        scrollManager.notifyItemInserted(index)
        if (scrollToLast) recyclerView.smoothScrollToPosition(index)
    }

    private fun onChatsUpdated(result: DiffUtil.DiffResult?) {
        transientBarDriver.toggleProgress(false)
        chatViewModel.updateLastSeen(team)
        if (result != null) scrollManager.onDiff(result)
    }

    private fun onScroll(dx: Int, dy: Int) {
        if (swappedTeam) return
        if (abs(dy) > 8) wasScrolling = true

        deferrer?.advanceDeadline()
        val date = chatViewModel.onScrollPositionChanged(team, scrollManager.firstVisiblePosition)

        if (date.isBlank()) dateHider?.hide()
        else dateHider?.show()

        val ref = dateHider?.view ?: return
        if (date == ref.text.toString()) return

        TransitionManager.beginDelayedTransition(
                ref.parent as ViewGroup,
                AutoTransition().addTarget(ref))

        ref.text = date
    }

    private fun onScrollStateChanged(newState: Int) {
        if (newState == SCROLL_STATE_DRAGGING) deferrer?.advanceDeadline()
        if (wasScrolling && newState == SCROLL_STATE_IDLE && isNearBottomOfChat) {
            unreadCount = 0
            updateUnreadCount()
            fetchChatsBefore(true)
        }
    }

    private fun updateUnreadCount() {
        if (unreadCount == 0)
            newMessageHider?.hide()
        else {
            newMessageHider?.view?.text = if (unreadCount == 1) getString(R.string.chat_new_message) else getString(R.string.chat_new_messages, unreadCount)
            newMessageHider?.show()
        }
    }

    companion object {

        fun newInstance(team: Team): ChatFragment = ChatFragment().apply { this.team = team }
    }
}

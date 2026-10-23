package dev.neffly.gesturelauncher.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dev.neffly.gesturelauncher.R
import dev.neffly.gesturelauncher.data.GestureAction
import dev.neffly.gesturelauncher.data.GestureMapping
import dev.neffly.gesturelauncher.data.GestureStore
import dev.neffly.gesturelauncher.ui.BaseActivity
import dev.neffly.gesturelauncher.ui.FontEngine
import dev.neffly.gesturelauncher.ui.GlassMenu
import dev.neffly.gesturelauncher.ui.overrideNextTransition

/** Management hub: list existing gestures, add new ones, edit (redraw / change app) and delete. */
class GestureSettingsActivity : BaseActivity() {

    private lateinit var adapter: GestureListAdapter
    private lateinit var emptyView: View
    private var parentGestureId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture_settings)
        parentGestureId = intent.getStringExtra(EXTRA_PARENT_ID)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            if (parentGestureId != null) {
                title = GestureStore.all(this@GestureSettingsActivity)
                    .firstOrNull { it.id == parentGestureId }?.label
                    ?: getString(R.string.gesture_action_two_step_folder)
            }
            setNavigationOnClickListener { finish() }
        }

        emptyView = findViewById(R.id.emptyView)
        val list = findViewById<RecyclerView>(R.id.gestureList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = GestureListAdapter(lifecycleScope) { mapping, anchor -> showRowMenu(mapping, anchor) }
        list.adapter = adapter

        findViewById<FloatingActionButton>(R.id.addButton).setOnClickListener {
            startActivity(GestureActionChooserActivity.newIntent(this, parentGestureId))
            // The chooser animates its own slide-in; suppress the OS's default cross-activity
            // transition the same way MainActivity does when opening the drawer.
            overrideNextTransition()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = GestureStore.all(this).filter { it.parentGestureId == parentGestureId }
        adapter.submit(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showRowMenu(mapping: GestureMapping, anchor: View) {
        val items = buildList {
            if (mapping.action == GestureAction.TWO_STEP_FOLDER) {
                add(GlassMenu.item(anchor, R.string.open_gesture_folder, R.drawable.ic_gesture) {
                    startActivity(folderIntent(this@GestureSettingsActivity, mapping.id))
                })
            }
            add(GlassMenu.item(anchor, R.string.redraw_gesture, R.drawable.ic_gesture) {
                startActivity(GestureTrainingActivity.redrawIntent(this@GestureSettingsActivity, mapping.id))
            })
            when (mapping.action) {
                GestureAction.LAUNCH_APP -> add(GlassMenu.item(anchor, R.string.change_app, R.drawable.ic_swap) {
                    startActivity(GestureTrainingActivity.changeAppIntent(this@GestureSettingsActivity, mapping.id))
                })
                GestureAction.OPEN_URL -> add(GlassMenu.item(anchor, R.string.edit_url, R.drawable.ic_link) {
                    startActivity(GestureUrlEntryActivity.editIntent(this@GestureSettingsActivity, mapping.id))
                })
                // Nothing to change beyond redrawing the shape.
                GestureAction.OPEN_DRAWER,
                GestureAction.QUICK_SEARCH,
                GestureAction.MEDIA_PLAY_PAUSE,
                GestureAction.MEDIA_NEXT,
                GestureAction.MEDIA_PREVIOUS,
                GestureAction.TWO_STEP_FOLDER -> {}
            }
            add(GlassMenu.item(anchor, R.string.delete, R.drawable.ic_delete, destructive = true) {
                deleteWithUndo(mapping)
            })
        }
        GlassMenu.show(anchor, items, xOffsetDp = MENU_INSET_DP)
    }

    private fun deleteWithUndo(mapping: GestureMapping) {
        val removed = GestureStore.all(this)
            .filter { it.id == mapping.id || it.parentGestureId == mapping.id }
        GestureStore.remove(this, mapping.id)
        refresh()
        Snackbar.make(findViewById(R.id.gestureList), R.string.gesture_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                removed.forEach { GestureStore.add(this, it) }
                refresh()
            }
            .also { FontEngine.applyTo(it.view) }
            .show()
    }

    companion object {
        private const val EXTRA_PARENT_ID = "parent_gesture_id"
        fun folderIntent(context: android.content.Context, folderId: String): Intent =
            Intent(context, GestureSettingsActivity::class.java).putExtra(EXTRA_PARENT_ID, folderId)

        /** The menu's start edge sits in from the row's, under the label rather than the icon. */
        private const val MENU_INSET_DP = 20
    }
}

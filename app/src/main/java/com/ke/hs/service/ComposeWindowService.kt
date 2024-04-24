package com.ke.hs.service

import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.SendAndArchive
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.ke.hs.R
import com.ke.hs.entity.CardBean
import com.ke.hs.parser.DeckCardObserver
import com.ke.hs.ui.CardView
import com.orhanobut.logger.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class ComposeWindowService : LifecycleService(), SavedStateRegistryOwner {


    private lateinit var frameLayout: FrameLayout
    private val windowManager: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }


    @Inject
    lateinit var deckCardObserver: DeckCardObserver


    private var show = true

    private fun showView() {
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        layoutParams.width = resources.getDimension(R.dimen.module_floating_window_width).toInt()
        layoutParams.height = resources.getDimension(R.dimen.module_floating_window_height).toInt()
        //需要设置 这个 不然空白地方无法点击
        layoutParams.flags =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParams.gravity = Gravity.START or Gravity.TOP
        layoutParams.format = PixelFormat.RGBA_8888//防止文字模糊

        savedStateRegistryController.performAttach() // you can ignore this line, becase performRestore method will auto call performAttach() first.
        savedStateRegistryController.performRestore(null)


        frameLayout = FrameLayout(this)
        frameLayout.setViewTreeLifecycleOwner(this)
        frameLayout.setViewTreeSavedStateRegistryOwner(this)
        val composeView = ComposeView(this)
        frameLayout.addView(composeView)
        frameLayout.setPadding(0, 0, 16, 16)
        frameLayout.setOnTouchListener(ScaleTouchListener(windowManager, frameLayout, layoutParams))
        composeView.setViewTreeSavedStateRegistryOwner(this)
        composeView.setViewTreeLifecycleOwner(this)

        composeView.setContent {
            FloatingComposeView(deckCardObserver = deckCardObserver, exitApp = {
//                exitProcess(0)
                stopSelf()
            }, toggle = {
                show = !show
                layoutParams.height = if (show) {
                    resources.getDimension(R.dimen.module_floating_window_height).toInt()
                } else {
                    resources.getDimension(R.dimen.module_floating_window_header_height).toInt()

                }
                windowManager.updateViewLayout(frameLayout, layoutParams)
            })
        }

        composeView.setOnTouchListener(
            ItemViewTouchListener(
                layoutParams,
                windowManager,
                frameLayout
            )
        )
        windowManager.addView(frameLayout, layoutParams)


    }

    override fun onCreate() {
        super.onCreate()
        deckCardObserver.init(lifecycleScope)

        showView()


    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(frameLayout)
    }


    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry


}

@Composable
private fun FloatingComposeView(
    deckCardObserver: DeckCardObserver,
    exitApp: () -> Unit,
    toggle: () -> Unit = {}
) {


    MaterialTheme {

        var cardListType by remember {
            mutableStateOf(CardListType.DECK)
        }


        var showAnalytics by remember {
            mutableStateOf(false)
        }

        var analyticsText by remember {
            mutableStateOf("")
        }

        val cardList by if (cardListType == CardListType.DECK) {
            deckCardObserver.deckCardList.collectAsState()
        } else if (cardListType == CardListType.MY_GRAVEYARD) {
            deckCardObserver.userGraveyardCardList.collectAsState()
        } else {
            deckCardObserver.opponentGraveyardCardList.collectAsState()
        }


        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(id = R.dimen.module_floating_window_header_height))
                    .background(
                        Color.Black.copy(
                            alpha = 0.3f
                        )
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = exitApp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ExitToApp,
                        contentDescription = null,
                        tint = Color.Red
                    )
                }

                IconButton(onClick = {
                    analyticsText = deckCardObserver.analytics()
                    showAnalytics = !showAnalytics

                }) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                IconButton(onClick = {
                    toggle()
                }) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Box {
                    var expanded by remember {
                        mutableStateOf(false)
                    }
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(text = "牌库") }, onClick = {
                            cardListType = CardListType.DECK
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(text = "自己墓地") }, onClick = {
                            cardListType = CardListType.MY_GRAVEYARD
                            expanded = false
                        })

                        DropdownMenuItem(text = { Text(text = "对手墓地") }, onClick = {
                            cardListType = CardListType.OPPONENT_GRAVEYARD
                            expanded = false
                        })

                    }
                }


            }

            if (showAnalytics) {
                Text(text = analyticsText, style = TextStyle(color = Color.White))
            } else {

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            Color.Black.copy(alpha = 0.5f)
                        )
                ) {
                    items(cardList) { card ->
                        CardView(card)
                    }
                }
            }
        }
    }

}

enum class CardListType {
    DECK, MY_GRAVEYARD, OPPONENT_GRAVEYARD
}

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
import androidx.compose.material3.Card
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
            FloatingComposeView(deckCardObserver = deckCardObserver, {
//                exitProcess(0)
                stopSelf()
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
private fun FloatingComposeView(deckCardObserver: DeckCardObserver, exitApp: () -> Unit) {


    MaterialTheme {

        var showAnalytics by remember {
            mutableStateOf(false)
        }

        var analyticsText by remember {
            mutableStateOf("")
        }

        val cardList by deckCardObserver.deckCardList.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(
                            alpha = 0.3f
                        )
                    )
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
                    items(cardList, {
                        it.card.id
                    }) { card ->
                        CardView(card)
                    }
                }
            }
        }
    }

}

@Preview(widthDp = 200)
@Composable
private fun CardViewPreview() {
    CardView(
        card = CardBean(
            com.ke.hs.entity.Card(name = "麻风侏儒", dbfId = 1, id = ""),
            count = 2
        )
    )
}

@Composable
private fun CardView(card: CardBean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
            Text(
                text = card.card.cost.toString(),
                style = TextStyle(color = Color.White, textAlign = TextAlign.Center),

                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .background(Color.DarkGray)
                    .wrapContentHeight()
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {


                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize(),
                    model = "https://art.hearthstonejson.com/v1/tiles/${card.card.id}.png",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = {
                        Logger.d("$it")
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = card.card.name, style = TextStyle(
                            color = Color.White
                        ), modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )

                    Text(
                        text = card.count.toString(),
                        style = TextStyle(color = Color.White, textAlign = TextAlign.Center),
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .background(
                                colorResource(
                                    id = card.card.rarity?.colorRes ?: R.color.module_mage
                                )
                            )
                            .wrapContentHeight()
                    )
                }

            }


        }

        HorizontalDivider(
            color = Color.Gray,
            thickness = 1.dp
        )
    }
}
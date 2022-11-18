package com.blockchain.home.presentation.activity.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.tablerow.custom.CustomTableRow
import com.blockchain.componentlib.tablerow.custom.StackedIcon
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.componentlib.utils.TextValue

fun ActivityIconState.toStackedIcon() = when (this) {
    is ActivityIconState.OverlappingPair.Local -> StackedIcon.OverlappingPair(
        front = ImageResource.Local(front),
        back = ImageResource.Local(back)
    )
    is ActivityIconState.OverlappingPair.Remote -> StackedIcon.OverlappingPair(
        front = ImageResource.Remote(front),
        back = ImageResource.Remote(back)
    )
    is ActivityIconState.SmallTag.Local -> StackedIcon.SmallTag(
        main = ImageResource.Local(main),
        tag = ImageResource.Local(tag)
    )
    is ActivityIconState.SmallTag.Remote -> StackedIcon.SmallTag(
        main = ImageResource.Remote(main),
        tag = ImageResource.Remote(tag)
    )
    is ActivityIconState.SingleIcon.Local -> StackedIcon.SingleIcon(
        icon = ImageResource.Local(res)
    )
    is ActivityIconState.SingleIcon.Remote -> StackedIcon.SingleIcon(
        icon = ImageResource.Remote(url)
    )
    ActivityIconState.None -> StackedIcon.None
}

sealed interface ActivityComponent {
    data class StackView(
        val leadingImage: ActivityIconState = ActivityIconState.None,
        val leading: List<ActivityStackView>,
        val trailing: List<ActivityStackView>
    ) : ActivityComponent

    data class Button(
        val value: TextValue,
        val style: ActivityButtonStyleState
    ) : ActivityComponent
}

@Composable
fun ActivityComponentItem(component: ActivityComponent, onClick: (() -> Unit)? = null) {
    when (component) {
        is ActivityComponent.Button -> {
            ActivityDetailButton(
                data = component,
                onClick = onClick
            )
        }
        is ActivityComponent.StackView -> {
            CustomTableRow(
                icon = component.leadingImage.toStackedIcon(),
                leadingComponents = component.leading.map { it.toViewType() },
                trailingComponents = component.trailing.map { it.toViewType() },
                onClick = onClick
            )
        }
    }
}

@Composable
fun ActivitySectionCard(
    modifier: Modifier = Modifier,
    components: List<ActivityComponent>,
    onClick: (() -> Unit)? = null
) {
    if (components.isNotEmpty()) {
        Card(
            backgroundColor = AppTheme.colors.background,
            shape = RoundedCornerShape(AppTheme.dimensions.mediumSpacing),
            elevation = 0.dp
        ) {
            Column(modifier = modifier) {
                components.forEachIndexed { index, transaction ->
                    ActivityComponentItem(component = transaction, onClick = onClick)

                    if (index < components.lastIndex) {
                        Divider(color = Color(0XFFF1F2F7))
                    }
                }
            }
        }
    }
}

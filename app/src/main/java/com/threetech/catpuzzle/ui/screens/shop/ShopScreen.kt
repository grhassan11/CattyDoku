package com.threetech.catpuzzle.ui.screens.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.ui.components.Button3D
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.CatIcon
import com.threetech.catpuzzle.ui.components.HintBulb
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.MouseIcon
import com.threetech.catpuzzle.ui.theme.FredokaFamily
import com.threetech.catpuzzle.ui.theme.GameColors

private val CardShape = RoundedCornerShape(20.dp)
private val BadgeShape = RoundedCornerShape(8.dp)

/** LazyColumn item index of each section: 0 = remove-ads card, 1..3 = packs, 4 = footer. */
private fun sectionIndex(section: Booster?): Int = when (section) {
    Booster.CAT -> 1
    Booster.HINT -> 2
    Booster.MOUSE -> 3
    null -> 0
}

/**
 * The Shop. Stateless: state + lambdas only. Prices are exactly the strings in [ShopUiState] (Play's localized
 * formattedPrice); a missing price shows a loading dash and its buy button is disabled.
 */
@Composable
fun ShopScreen(
    state: ShopUiState,
    snackbarHostState: SnackbarHostState,
    initialSection: Booster?,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onBuy: (productId: String) -> Unit,
    onManageSubscription: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = sectionIndex(initialSection))
    Box(modifier.fillMaxSize().background(GameColors.ScreenBackground)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ShopTopBar(restoreEnabled = !state.busy, onBack = onBack, onRestore = onRestore)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "ads") {
                    Column {
                        if (state.catalog == CatalogStatus.UNAVAILABLE) {
                            UnavailableNotice(onRetry)
                            Spacer(Modifier.height(16.dp))
                        }
                        RemoveAdsCard(state, onBuy, onManageSubscription)
                    }
                }
                for (section in state.sections) {
                    item(key = "section_${section.booster.name}") {
                        PackSection(section, state.catalog, onBuy)
                    }
                }
                item(key = "footer") {
                    ShopFooter(restoreEnabled = !state.busy, onRestore = onRestore, onTerms = onTerms, onPrivacy = onPrivacy)
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(16.dp),
        )
    }
}

// ------------------------------------------------------------------ top bar / footer

@Composable
private fun ShopTopBar(restoreEnabled: Boolean, onBack: () -> Unit, onRestore: () -> Unit) {
    val backDescription = stringResource(R.string.shop_back)
    val title = stringResource(R.string.shop_title)
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp)) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .semantics { contentDescription = backDescription },
            shape = CircleShape,
            color = GameColors.Card,
            shadowElevation = 2.dp,
            onClick = onBack,
        ) {
            Box(contentAlignment = Alignment.Center) {
                IconImage(R.drawable.ic_back, size = 24.dp, tint = GameColors.Body)
            }
        }
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
            color = GameColors.Title,
            fontSize = 24.sp,
            fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold,
        )
        TextButton(
            onClick = onRestore,
            enabled = restoreEnabled,
            modifier = Modifier.align(Alignment.CenterEnd).heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.shop_restore),
                color = if (restoreEnabled) GameColors.CtaGreenLip else GameColors.Disabled,
                fontSize = 16.sp,
                fontFamily = FredokaFamily, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ShopFooter(restoreEnabled: Boolean, onRestore: () -> Unit, onTerms: () -> Unit, onPrivacy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FooterLink(stringResource(R.string.shop_footer_restore), onRestore, enabled = restoreEnabled)
        FooterDot()
        FooterLink(stringResource(R.string.shop_footer_terms), onTerms)
        FooterDot()
        FooterLink(stringResource(R.string.shop_footer_privacy), onPrivacy)
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(text = text, color = GameColors.Body, fontSize = 14.sp, fontFamily = FredokaFamily, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FooterDot() {
    Text(text = "·", color = GameColors.Body, fontSize = 14.sp)
}

@Composable
private fun UnavailableNotice(onRetry: () -> Unit) {
    Surface(shape = CardShape, color = GameColors.RuleCard) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.shop_unavailable),
                modifier = Modifier.weight(1f),
                color = GameColors.Title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    text = stringResource(R.string.shop_try_again),
                    color = GameColors.CtaGreenLip,
                    fontFamily = FredokaFamily, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ remove ads

@Composable
private fun RemoveAdsCard(state: ShopUiState, onBuy: (String) -> Unit, onManage: () -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.shop_remove_ads_header).uppercase())
        Spacer(Modifier.height(8.dp))
        Surface(shape = CardShape, color = GameColors.Card, shadowElevation = 1.dp) {
            Column {
                MonthlyRow(state.monthly, state.catalog, onBuy, onManage)
                HorizontalDivider(color = GameColors.RuleCard)
                ForeverRow(state.forever, state.catalog, onBuy)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.shop_rewarded_caption),
            modifier = Modifier.fillMaxWidth(),
            color = GameColors.Body,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MonthlyRow(offer: AdsOfferUi, catalog: CatalogStatus, onBuy: (String) -> Unit, onManage: () -> Unit) {
    val title = stringResource(R.string.shop_monthly_title)
    val priceText = offer.price?.let { stringResource(R.string.shop_monthly_price, it) }
    OfferRow(
        title = title,
        description = stringResource(R.string.shop_monthly_desc),
        badge = PackBadge.NONE,
        price = priceText,
        catalog = catalog,
        priceColor = GameColors.CtaGreenLip,
        showPrice = offer.ownership != OfferOwnership.ACTIVE,
    ) {
        when (offer.ownership) {
            OfferOwnership.ACTIVE -> {
                val manageDescription = stringResource(R.string.shop_manage_description)
                StateChip(stringResource(R.string.shop_active))
                Spacer(Modifier.width(8.dp))
                ShopButton(
                    text = stringResource(R.string.shop_manage),
                    description = manageDescription,
                    enabled = true,
                    style = ButtonStyle.Secondary,
                    onClick = onManage,
                )
            }
            else -> ShopButton(
                text = stringResource(R.string.shop_buy),
                description = buyDescription(title, priceText),
                enabled = offer.buyEnabled,
                style = ButtonStyle.Green,
                onClick = { onBuy(offer.productId) },
            )
        }
    }
}

@Composable
private fun ForeverRow(offer: AdsOfferUi, catalog: CatalogStatus, onBuy: (String) -> Unit) {
    val title = stringResource(R.string.shop_forever_title)
    OfferRow(
        title = title,
        description = stringResource(R.string.shop_forever_desc),
        badge = PackBadge.BEST_VALUE,
        price = offer.price,
        catalog = catalog,
        priceColor = GameColors.Coral,
        showPrice = offer.ownership != OfferOwnership.OWNED,
    ) {
        if (offer.ownership == OfferOwnership.OWNED) {
            StateChip(stringResource(R.string.shop_owned))
        } else {
            ShopButton(
                text = stringResource(R.string.shop_buy),
                description = buyDescription(title, offer.price),
                enabled = offer.buyEnabled,
                style = ButtonStyle.Primary,
                onClick = { onBuy(offer.productId) },
            )
        }
    }
}

@Composable
private fun OfferRow(
    title: String,
    description: String,
    badge: PackBadge,
    price: String?,
    catalog: CatalogStatus,
    priceColor: Color,
    showPrice: Boolean,
    action: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, color = GameColors.Title, fontSize = 20.sp, fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold)
                if (badge != PackBadge.NONE) {
                    Spacer(Modifier.width(8.dp))
                    Badge(badge)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = description, color = GameColors.Body, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (showPrice) {
            PriceText(price, catalog, priceColor)
            Spacer(Modifier.width(10.dp))
        }
        action()
    }
}

// ------------------------------------------------------------------ packs

@Composable
private fun PackSection(section: PackSectionUi, catalog: CatalogStatus, onBuy: (String) -> Unit) {
    val copy = sectionCopy(section.booster)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(copy.title).uppercase())
                Text(
                    text = stringResource(copy.subtitle),
                    color = GameColors.Body,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            BalanceChip(section.booster, section.balance)
        }
        Spacer(Modifier.height(8.dp))
        Surface(shape = CardShape, color = GameColors.Card, shadowElevation = 1.dp) {
            Column {
                section.packs.forEachIndexed { index, pack ->
                    if (index > 0) HorizontalDivider(color = GameColors.RuleCard)
                    PackRow(pack, copy.packLabel, catalog, onBuy)
                }
            }
        }
    }
}

@Composable
private fun PackRow(pack: PackUi, labelRes: Int, catalog: CatalogStatus, onBuy: (String) -> Unit) {
    val label = stringResource(labelRes, pack.amount)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = GameColors.Title, fontSize = 20.sp, fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold)
            if (pack.badge != PackBadge.NONE) {
                Spacer(Modifier.width(8.dp))
                Badge(pack.badge)
            }
        }
        PriceText(
            price = pack.price,
            catalog = catalog,
            color = if (pack.badge == PackBadge.BEST_VALUE) GameColors.Coral else GameColors.CtaGreenLip,
        )
        Spacer(Modifier.width(10.dp))
        ShopButton(
            text = stringResource(R.string.shop_buy),
            description = buyDescription(label, pack.price),
            enabled = pack.buyEnabled,
            style = ButtonStyle.Green,
            onClick = { onBuy(pack.productId) },
        )
    }
}

@Composable
private fun BalanceChip(booster: Booster, balance: Int) {
    val description = stringResource(R.string.shop_balance_description, balance)
    Surface(
        shape = CircleShape,
        color = GameColors.Card,
        shadowElevation = 1.dp,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 40.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (booster) {
                Booster.CAT -> CatIcon(size = 24.dp)
                Booster.HINT -> HintBulb(size = 24.dp)
                Booster.MOUSE -> MouseIcon(size = 24.dp)
            }
            Spacer(Modifier.width(8.dp))
            Text(text = balance.toString(), color = GameColors.Title, fontSize = 18.sp, fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold)
        }
    }
}

private class SectionCopy(val title: Int, val subtitle: Int, val packLabel: Int)

private fun sectionCopy(booster: Booster): SectionCopy = when (booster) {
    Booster.CAT -> SectionCopy(R.string.shop_cats_title, R.string.shop_cats_subtitle, R.string.shop_pack_cats)
    Booster.HINT -> SectionCopy(R.string.shop_hints_title, R.string.shop_hints_subtitle, R.string.shop_pack_hints)
    Booster.MOUSE -> SectionCopy(R.string.shop_mice_title, R.string.shop_mice_subtitle, R.string.shop_pack_mice)
}

// ------------------------------------------------------------------ small parts

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        color = GameColors.Body,
        fontSize = 14.sp,
        fontFamily = FredokaFamily, fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
    )
}

@Composable
private fun Badge(badge: PackBadge) {
    val (textRes, color) = when (badge) {
        PackBadge.BEST_VALUE -> R.string.shop_badge_best_value to GameColors.Coral
        PackBadge.POPULAR -> R.string.shop_badge_popular to GameColors.CtaGreenLip
        PackBadge.NONE -> return
    }
    Text(
        text = stringResource(textRes),
        modifier = Modifier
            .clip(BadgeShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        fontSize = 11.sp,
        fontFamily = FredokaFamily, fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    )
}

/** Localized Play price, or a placeholder while loading / when unavailable. */
@Composable
private fun PriceText(price: String?, catalog: CatalogStatus, color: Color) {
    val text = when {
        price != null -> price
        catalog == CatalogStatus.LOADING -> stringResource(R.string.shop_price_loading)
        else -> stringResource(R.string.shop_price_unavailable)
    }
    Text(
        text = text,
        color = if (price != null) color else GameColors.Body,
        fontSize = 16.sp,
        fontFamily = FredokaFamily, fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End,
    )
}

@Composable
private fun StateChip(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .background(GameColors.ToggleOn.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = GameColors.CtaGreenLip,
        fontSize = 15.sp,
        fontFamily = FredokaFamily, fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun buyDescription(item: String, price: String?): String =
    if (price != null) stringResource(R.string.shop_buy_description, item, price) else item

@Composable
private fun ShopButton(
    text: String,
    description: String,
    enabled: Boolean,
    style: ButtonStyle,
    onClick: () -> Unit,
) {
    Button3D(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.defaultMinSize(minWidth = 84.dp),
        style = if (enabled) style else ButtonStyle.Locked,
        contentDescription = description,
        markDisabled = !enabled,
        horizontalPadding = 16.dp,
        totalHeight = 46.dp,
        lipHeight = 5.dp,
    ) {
        Text(
            text = text,
            color = if (enabled) style.content else ButtonStyle.Locked.content,
            fontSize = 19.sp,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                lineHeight = 22.sp,
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
                shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), androidx.compose.ui.geometry.Offset(0f, 2f), 3f),
            ),
        )
    }
}

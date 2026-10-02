# AutoFlip GE Setup UI Asset Pack v1

These are individual transparent PNG assets, not a single full-screen mockup.
They are intended to be copied into the plugin resource folder and rendered as movable overlay images.

Recommended plugin resource path:
`src/main/resources/autoflip/setup_ui/`

Recommended runtime config fields later:
- `setup.asset.logo.x/y/w/h`
- `setup.asset.qty.hint.x/y/w/h`
- `setup.asset.price.hint.x/y/w/h`
- `setup.asset.review.bar.x/y/w/h`
- `setup.asset.pick.tile.x/y/w/h`
- `setup.asset.back.arrow.x/y/w/h`

Assets:

- `autoflip_logo_watermark_bottom_right.png` — bottom-right logo with subtle green dotted haze.
- `autoflip_logo_clean_moveable.png` — clean movable logo without background haze.
- `pick_item_tile_bg.png` — green-bordered item tile background.
- `icon_cart_pick_item.png` — cart icon for pick-item tile.
- `icon_quantity_crosshair.png` — icon for quantity hint.
- `icon_price_coins.png` — icon for price hint.
- `icon_review_shield.png` — icon for review/confirm message.
- `hint_panel_quantity_bg.png` — empty quantity hint panel background; plugin should draw text over it.
- `hint_panel_price_bg.png` — empty price hint panel background; plugin should draw text over it.
- `review_message_bar_bg.png` — empty review message strip; plugin should draw text over it.
- `input_frame_quantity.png` — optional decorative quantity input frame.
- `input_frame_price.png` — optional decorative price input frame.
- `final_price_bar_bg.png` — optional decorative final-price bar.
- `icon_back_arrow_gold.png` — clean back arrow asset if we stop relying on native arrow visuals.
- `bottom_gold_accent_line.png` — subtle bottom accent line.
- `corner_flourish_bottom_left.png` — decorative bottom-left corner.
- `corner_flourish_bottom_right.png` — decorative bottom-right corner.

Implementation note:
Keep `...` buttons as holes/click-through. These images should be rendered only when `setup.custom.ui.enabled=1` and AutoFlip A is active.

# Knife construction

Measurements below are what the meshes are built to. Photographs were used only to read proportions. None of them are shipped.

The silhouette rules are the same ones the generator checks. A positive handle angle means the butt drops toward the edge. A positive spine offset means the handle's reference line sits toward the edge from the spine (the handle is below the spine when the edge is down).

## Chef's knife

Western forged 8 inch. One piece of steel: blade, integral bolster, full tang. The scales are wood on either side of that tang.

The spine at the heel is a straight line, and that line continues into the top of the handle. Makers put the tang axis parallel to the spine at the heel and treat a handle that cants down off that line as a chopper, not a chef's knife. A slight drop of the butt is normal. Knuckle clearance comes from the heel height and from the handle sitting high, not from hanging the grip in the middle of the blade.

| | Built | Accepted range |
| --- | --- | --- |
| Blade | 200 mm long, 48 mm at the heel, tip 16 mm above the edge | the blade outline |
| Handle length, heel to butt | 130 mm, including the bolster | 0.55–0.75 of the blade |
| Handle top vs spine at the front | flush, then about 3 mm of drop by the butt | offset −0.6 to +4 mm, angle −1° to +3.5° |
| Handle girth | 26 mm at the bolster, 32 mm at the swell, slight flare at the butt | about 24–34 mm |
| Handle thickness | about 20 mm across the swell, oval, thinner at the butt | about 16–22 mm |
| Bolster | same steel, about 8.6 mm thick, sloping back to the 2.7 mm spine | thicker than the blade |
| Finger guard | quarter-ellipse from the heel up to the handle bottom | smooth concave, no V and no step |
| Distal taper | 2.7 mm at the heel, 1.0 mm at the tip | 2.5–3 mm down to about 1 mm |
| Rivets | three, on the handle centreline, about 34 mm apart, flush | full tang |

The full tang is the handle outline plus about 1.3 mm, so a steel stripe shows on the top, the bottom, and the butt.

Sources: Wüsthof describes a forged knife as one steel blank whose bolster is the thickened junction and whose tang runs through the handle ([kitchenknives.co.uk](https://www.kitchenknives.co.uk/know-how/blog/discovering-the-anatomy-of-a-classic-8-inch-20cm-chefs-knife-with-wusthof/), [Wüsthof](https://wusthof.com/blogs/the-chefs-table/forged-versus-non-forged-kitchen-knives), [Wüsthof catalogue](https://content.abt.com/documents/6208/WUESTHOF_EN.pdf)). TechGearLab measured the Classic 8 inch and the Zwilling Professional S spine at 2.60 mm ([review](https://www.techgearlab.com/reviews/kitchen/chef-knife/wusthof-classic-8)). A BladeForums maker's layout for an 8 inch blade used a 4.3 inch handle, and the replies tell him not to cant that handle down ([thread](https://www.bladeforums.com/threads/good-handle-length-for-8-chefs-knife-integral-bolster-without-forging-or-welding-and-good-behind-edge-thickness.1997900/)). Kitchen Knife Forums handle notes put a comfortable western grip around 24–28 mm tall and 16–20 mm wide, with the thick stock at the neck ([ergonomics](https://www.kitchenknifeforums.com/threads/ergonomics-what-parameters-makes-a-western-handle-good.44681/), [distal taper](https://www.kitchenknifeforums.com/threads/understanding-distal-taper.43802/)). Gyuto makers state the same spine rule: the tang axis is parallel to the spine, and the edge is what rises for finger clearance ([BladeForums](https://www.bladeforums.com/threads/gyuto-handle-angle.1650215/), [Kalisky](http://matuskalisky.blogspot.com/2018/02/thoughts-on-designing-240-wa-gyuto.html)). SharpEdge describes the bolster as the thick transition that tapers from the handle to the heel ([parts of a knife](https://sharpedgeshop.com/blogs/knives-101/parts-of-japanese-kitchen-knife)).

## Throwing knife

One piece of steel. The handle is the rear half of the same bar, on the centreline, symmetric, with no guard. Cord is wound on that bar. Balance stays at the middle, which is what a predictable spin wants.

| | Built | Accepted range |
| --- | --- | --- |
| Overall | 280 mm, blade half 140 mm, 28 mm wide | handle/blade 0.90–1.10 |
| Axis | the centreline | angle −1° to +1°, offset −0.6 to +0.6 mm |
| Section | diamond, 5 mm, recessed under the cord | stock thickness |

Sources: knifethrowing.info puts the centre of gravity within 1.5 cm of the lengthwise middle and draws the handle as the continuation of a symmetric profile ([balance](https://www.knifethrowing.info/throwing-knife-balance.html), [handbook](http://knifethrowing.info/throwing_knives.html)).

## Pocket knife

Open drop-point folder, 85 × 24 mm blade, about 116 mm of handle. The back of the handle is the spine line. The blade, including the edge, sits inside the handle height so the edge is covered when the knife is shut. The tang lies in the channel and the pivot pin goes through the tang and both liners.

| | Built | Accepted range |
| --- | --- | --- |
| Handle / blade | about 1.36 | 1.05–1.50 |
| Handle back vs blade spine | flush, within about 1 mm | offset −1.5 to +1.5 mm, angle −2° to +2.5° |

Source: Alistair Phillips' slipjoint layout says to align the spine of the open blade with the back of the handle, and to keep the closed blade inside the handle ([design notes](http://www.knives.mutantdiscovery.com/slipjoint1.html)).

## Butterfly knife

Shelved for the 1.1.12 picker. The mesh builder stays in `generate_models.py` and exports only when `KNIFE_SHELVED=1`. The APK does not include `butterfly.glb`.

Open balisong. The 100 × 22 mm clip-point blade is centred between the two channel handles, in thickness and in the side view. The tang sits in the channel at the pivot, the kicker meets the handle, and the latch is at the butt.

| | Built | Accepted range |
| --- | --- | --- |
| Handle centre vs blade centre | the same line, −11 mm from the edge | offset −1.2 to +1.2 mm, angle −2° to +2° |
| Handle / blade | about 1.4 | 1.15–1.60 |

Sources: a closed balisong is centred when the blade sits evenly between the handles rather than against one of them ([tuning](https://balisongbutterfly.com/how-to-tune-a-balisong)). The tang is the part between the handles at the pivot, and the kicker is the stop on that tang ([anatomy](https://balisongbutterfly.com/balisong-anatomy-parts-explained), [Benchmade](https://support.benchmade.com/hc/en-us/articles/24423264139035-Balisong-Anatomy)).

## Cleaver

Shelved for the 1.1.12 picker, same as the butterfly knife. `cleaver.glb` is not in the app assets.

180 × 90 mm rectangle, 5 mm at the heel tapering to about 3.6 mm at the tip. The handle is at the top of the heel, about 33 mm tall and 112 mm along the wood, with its top on the spine. A long handle on a tall blade is the usual cai dao proportion: the grip does not sit in the middle of the blade. The eye is near the top front corner. The ferrule covers the top of the heel and the front of the scales.

| | Built | Accepted range |
| --- | --- | --- |
| Handle top vs spine | about 1 mm below | offset −0.8 to +4 mm, angle −1° to +3.5° |
| Handle / blade | about 0.62 on the scales, 120 mm overall in the physics part | 0.55–0.80 |
| Handle height | 33 mm | the measured cai dao handles are about 31–34 mm |

Sources: a cai dao is about 175–200 mm by 90–100 mm ([Xinzuo](https://xinzuo.com.au/blogs/news/cai-dao-chinese-vegetable-cleaver)). Gesshin's 220 mm cleaver has a 110 mm handle, 34 mm tall, on a 103 mm blade; the small one is 88 mm by 31 mm on an 80 mm blade ([Japanese Knife Imports](https://www.japaneseknifeimports.com/products/gesshin-220mm-chinese-cleaver)). Takeda's measured chukabocho is about 89 mm tall at the handle with a 130 mm handle ([zknives](https://zknives.com/knives/kitchen/ktknv/takeda/tkcleavervg210.shtml)).

## What did not move

`FlipPhysics.kt` still uses the same part masses, stations, spans, and heights. Those already match these lengths: chef blade 200 mm and handle part 125 mm, throwing knife one 280 mm bar, pocket blade 85 mm and handle 110 mm, butterfly blade 100 mm and handles 140 mm, cleaver blade 180 mm and handle 120 mm. Moving a handle up to the spine changes where the steel sits across the blade, which this one-dimensional model does not store. The practised toss is unchanged. Distal taper is a thickness along the spine, and the inertia uses the height across the blade, not that thickness.

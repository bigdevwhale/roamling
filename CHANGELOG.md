# Changelog

<!--
Telegram: end each version's section with a hidden block for the StillCraft chat. Start it with a
line "<!-- tg", then 2-4 short lines in Russian for users (what is new, what is fixed), no markdown,
then close it like any HTML comment. The release notes carry it, and the chat bot posts those lines.
-->

## [1.2.1] - 2026-11-22

### Changed
- **Who wears what (#9):** in a companion's gear list, a piece another companion wears says so – «Shurshik wears it now». Put it on anyway and you're told who goes without it, so nobody is left bare in the frost by accident.

<!-- tg
В снаряжении видно, на ком уже надета вещь, – и если переодеть её на другого спутника, приложение скажет, кто остался без неё
-->

## [1.2.0] - 2026-11-20

### Added
- **Swap a daily task:** one that can't be done today (a walk by the water with no water near) can be traded once for another of a different kind, for 15 cones (#8).
- **The map turns with two fingers**, as in Fogwalk, once they plainly twist; the compass button sets north back up (#3).
- **Totals open up:** in the journal's Totals, the companions and the finds unfold into lists – who walked how far with you, what was found where and when (#4).

### Changed
- **Shadows stand on pavements:** a shadow still appears where it did, but when a way you've walked before passes within 80 m, it stands on it, not inside a fenced school yard (#3).
- **The nose explained:** the leader's first scent says to tap it, and the tap opens the nose; the nose tells what the arrow and the map's find icon mean; the hint about food says plainly that new places feed best (#4).
- **Weak GPS said plainly:** with an accuracy worse than ±35 m the map says that fog and steps don't count until it sharpens (#2).

### Fixed
- The den's picture reaches the bottom on shorter screens; the night sky no longer shows in a stripe under it (#5).

<!-- tg
Дневное задание теперь можно сменить на другое за 15 шишек, а карту – поворачивать двумя пальцами, как в Fogwalk
Тучки встают на тротуары, по которым вы уже ходили, а не во дворы школ
Понятнее про «Нюх» и еду, в «Итогах» раскрываются попутчики и находки, при слабом GPS карта так и пишет
Исправлено: под картинкой логова больше нет фиолетовой полосы
-->

## [1.1.0] - 2026-11-18

### Added
- **Moving with Lingvale:** Lingvale can now gather the backups of all the family's apps into one file and load them on a new phone. Roamling hands it the same file the settings save and loads it back the same way, only when you confirm it in Lingvale. A walk in progress has to be finished first.

<!-- tg
Попутчики переезжают вместе со всей деревней: Lingvale собирает копии всех приложений в один файл и загружает их на новом телефоне.
-->

## [1.0.1] - 2026-11-18

### Added
- **Postcards:** your road so far, the backpack, each companion in its own world, a walk with its route and weather stamp, the bestiary, the weather stamps, the den and the Great Map. Swipe between them and share. The button is in the Journal's banner, on a companion's page and on a walk's page.
- **Moving to another phone:** Settings → Walks → save all the progress to one file (companions, eggs, walks with routes, the fog, finds, cones) and load it on the new phone.

### Fixed
- **Too many eggs in the pockets (5/2):** when the phone delivered a burst of GPS points at once (after the screen was off), each of them could find the pockets free and the same patch new. Points are now taken strictly one by one. Eggs already over the limit simply hatch; new ones come when there is room.
- **Grown companions in Lingvale:** the village showed every grown companion in its species' first form. Now it gets the form each one grew into.

<!-- tg
Попутчики 1.0.1: открытки и перенос на другой телефон.
Открытки – итоги пути, рюкзак, каждый попутчик, прогулка с маршрутом, бестиарий, марки погоды, логово и Большая карта. Кнопка – в Дневнике, на странице попутчика и прогулки.
Весь прогресс можно сохранить в файл и загрузить на новом телефоне: Настройки → Прогулки.
Исправлено: в карманах больше не набирается лишних яиц.
-->

## [1.0.0] - 2026-11-18

First release.

- **70 species, nearly 200 grown forms.** Companions hatch from eggs found in the fog or come by themselves in their weather and hour. A teen grows into the form of what it ate: green, water, stone, height, dusk or sky.
- **Fog over a real map** (GPS handling from Fogwalk): new patches feed five times more than steps; districts, their kind and their sleeping spirit are on the map.
- **The story:** districts as chapters, spirits that wake and seal the Great Map, twelve letters of the First Walker, and the Keeper of the fog.
- **Real weather** from Open-Meteo: the sky over the rest stop, weather stamps, wild companions of rain, fog, snow, heat and rainbows.
- **The nose** to real places from OpenStreetMap, three daily and three weekly tasks, the leader's wish, a backpack for three with eggs in its pockets, the den, a shop with gear and decor.
- **Steps only on walks:** the phone's sensor, plus Health Connect for the walk's window if you turn it on.
- An onboarding you can play: wipe the fog with a finger and watch a spirit wake; short hints on every screen.
- Light and dark themes, Russian and English. Lingvale bridge.

<!-- tg
Новое приложение – «Попутчики»: ходилка с питомцами.
Вы гуляете по городу – туман на карте тает, а маленькие попутчики растут. 70 видов, и каждый вырастает по-своему: смотря где вы гуляли – в парке, у воды, по дворам, ночью или под дождём.
Есть сюжет: районы просыпаются, а на тропах находятся письма того, кто укрыл город туманом.
-->

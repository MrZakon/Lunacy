# LunacyVisual

Клиентский Fabric-мод для Minecraft 26.1.2.
Мод не добавляет сетевой протокол и не требуется на сервере.

## Сборка

Требуется JDK 25 или новее. Итоговый JAR создаётся командой:

```powershell
.\gradlew.bat build
```

Артефакт появляется в `build/libs/`. Конфигурация клиента хранится в
`.minecraft/config/lunacy_visuals.json`; запись выполняется атомарной заменой
с debounce, а повреждённый JSON переносится в резервный файл.

## Управление

- `Right Shift` — открыть ClickGUI.
- ЛКМ по карточке — включить или выключить модуль.
- ПКМ — открыть настройки.
- СКМ — начать назначение клавиши.
- В ClickGUI доступен HUD Builder с сеткой 4 px, перетаскиванием и привязкой к центру.

## Архитектура

- `ClientRuntime` владеет жизненным циклом EventBus, реестра модулей, конфигурации и GPU-ресурсов.
- EventBus один раз строит bound `MethodHandle` для каждого `@Subscribe`; горячая публикация использует кэшированные массивы слушателей.
- `RenderEngine` управляет direct-GLSL программами и MSDF-метриками. Все GL program objects освобождаются при остановке клиента.
- `ConfigManager` сохраняет состояния, настройки, keybind и позиции HUD.
- Fabric callbacks отвечают за tick/HUD/world-фазы; mixin-инъекции оставлены только для недоступных через API точек.

## 20 модулей

1. Watermark
2. ArrayList
3. TargetHUD
4. ArmorHUD
5. Keybinds HUD
6. Custom Crosshair
7. Hands & ViewModel
8. MotionTrails
9. ChinaHat / Halo
10. Jump & Hit Particles
11. Ambience / Custom World
12. Fullbright / NightVision
13. Motion Blur & Camera FX
14. Custom Scoreboard & BossBar
15. Custom Chat HUD
16. HitColor / Damage Flash
17. Item Glint & Block Overlay FX
18. Custom Nametags
19. Block Highlight FX
20. Cape & Wings Engine

Ресурсный пакет расположен в `src/main/resources/assets/lunacyvisuals`: шейдеры,
MSDF-атласы, фон и логотип меню, Lottie JSON, частицы и звуки перенесены из
предоставленного каталога `source` с заменой namespace.

Фирменная аватарка хранится в `assets/lunacyvisuals/avatar.png` и используется
как иконка Fabric-мода, логотип splash-screen и центральный логотип главного меню.

## Visual Studio update

Добавлены пресеты Eclipse/Sakura/Frost/Minimal, переносимые и серверные профили,
примерочная (`K`), фоторежим (`F8`), осмотр предмета (`G`), след оружия,
призрачные силуэты, приземление, атмосфера биомов, анимированный хотбар,
умный HUD, подсказки, реакции спутника и адаптивные частицы. Выровнен набор
модулей: Death Effects, Damage Indicators и Trajectory Prediction доступны в 26.x.
HUD Builder получил группы по ПКМ, привязку к виджетам и `Ctrl+Z`.

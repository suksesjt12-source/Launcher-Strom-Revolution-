#!/usr/bin/env python3
"""Install the Storm-specific home screen into the pinned Winlator Mali source tree."""
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch_storm_home.py <winlator-source-root>")

root = Path(sys.argv[1]).resolve()
main_activity = root / "app/src/main/java/com/winlator/cmod/MainActivity.java"
menu_file = root / "app/src/main/res/menu/main_menu.xml"
source_fragment = Path("launcher-patches/StormHomeFragment.java")
target_fragment = root / "app/src/main/java/com/winlator/cmod/StormHomeFragment.java"

for required in (main_activity, menu_file, source_fragment):
    if not required.is_file():
        raise SystemExit(f"Required patch input not found: {required}")

main = main_activity.read_text(encoding="utf-8")
menu = menu_file.read_text(encoding="utf-8")

old_default = "int menuItemId = selectedMenuItemId > 0 ? selectedMenuItemId : R.id.main_menu_containers;"
new_default = "int menuItemId = selectedMenuItemId > 0 ? selectedMenuItemId : R.id.main_menu_storm_home;"
if old_default in main:
    main = main.replace(old_default, new_default, 1)
elif new_default not in main:
    raise SystemExit("Could not locate MainActivity default section selection.")

old_switch = """            case R.id.main_menu_shortcuts:
                show(new ShortcutsFragment(), false);  // Forward animation
                break;"""
new_switch = """            case R.id.main_menu_storm_home:
                show(new StormHomeFragment(), false);
                break;
            case R.id.main_menu_shortcuts:
                show(new ShortcutsFragment(), false);  // Forward animation
                break;"""
if old_switch in main:
    main = main.replace(old_switch, new_switch, 1)
elif "case R.id.main_menu_storm_home:" not in main:
    raise SystemExit("Could not locate MainActivity navigation switch.")

old_sections = """    private static final int[] CONTROLLER_SECTIONS = {
        R.id.main_menu_shortcuts, R.id.main_menu_containers, R.id.main_menu_input_controls,"""
new_sections = """    private static final int[] CONTROLLER_SECTIONS = {
        R.id.main_menu_storm_home, R.id.main_menu_shortcuts, R.id.main_menu_containers, R.id.main_menu_input_controls,"""
if old_sections in main:
    main = main.replace(old_sections, new_sections, 1)
elif "R.id.main_menu_storm_home, R.id.main_menu_shortcuts" not in main:
    raise SystemExit("Could not add Storm Home to controller navigation sections.")

old_menu = """            <group android:id="@+id/group_emulation" android:checkableBehavior="single">
                <item
                    android:id="@+id/main_menu_shortcuts""" 
new_menu = """            <group android:id="@+id/group_emulation" android:checkableBehavior="single">
                <item
                    android:id="@+id/main_menu_storm_home"
                    android:icon="@drawable/icon_shortcut"
                    android:title="Storm Revolution" />
                <item
                    android:id="@+id/main_menu_shortcuts"""
if old_menu in menu:
    menu = menu.replace(old_menu, new_menu, 1)
elif "main_menu_storm_home" not in menu:
    raise SystemExit("Could not add Storm Home to the navigation menu.")

shutil.copyfile(source_fragment, target_fragment)
main_activity.write_text(main, encoding="utf-8")
menu_file.write_text(menu, encoding="utf-8")

print("Installed StormHomeFragment and made it the default home screen.")

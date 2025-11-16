# UI Changes for Offline Mode Feature

## Settings Screen - Before and After

### Before
```
┌─────────────────────────────────┐
│  ← Settings                     │
├─────────────────────────────────┤
│                                 │
│  Account                        │
│  ├─ Push Notifications    [ON] │
│  ├─ Privacy Policy           → │
│  └─ Delete Account           → │
│                                 │
│  Appearance                     │
│  └─ Light / Dark Mode     [ON] │
│                                 │
│  Data                           │
│  ├─ Clear all cached data    → │
│  └─ Logout                   → │
│                                 │
└─────────────────────────────────┘
```

### After (with Offline Mode)
```
┌─────────────────────────────────┐
│  ← Settings                     │
├─────────────────────────────────┤
│                                 │
│  Account                        │
│  ├─ Push Notifications    [ON] │
│  ├─ Privacy Policy           → │
│  └─ Delete Account           → │
│                                 │
│  Appearance                     │
│  └─ Light / Dark Mode     [ON] │
│                                 │
│  Data                           │
│  ├─ Offline Mode         [OFF] │ ← NEW!
│  │   Prevent network requests  │
│  │   and work with cached      │
│  │   data only                 │
│  ├─ Clear all cached data    → │
│  └─ Logout                   → │
│                                 │
└─────────────────────────────────┘
```

## UI Component Details

### Offline Mode Toggle

**Component Type:** LinearLayout with MaterialSwitch

**Layout:**
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">

    <!-- Toggle Row -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:gravity="center_vertical">
        
        <TextView
            android:text="@string/offline_mode"
            android:textSize="16sp"
            android:textColor="@color/text_primary" />
        
        <MaterialSwitch
            android:id="@+id/switchOfflineMode"
            android:checked="false" />
    </LinearLayout>

    <!-- Description Text -->
    <TextView
        android:text="@string/offline_mode_description"
        android:textSize="13sp"
        android:textColor="@color/text_secondary"
        android:alpha="0.8" />
</LinearLayout>
```

**Visual Characteristics:**
- Primary text: "Offline Mode" (16sp, primary color)
- Secondary text: "Prevent network requests and work with cached data only" (13sp, secondary color, 80% opacity)
- Material Switch: Standard Material 3 design
- Height: 56dp for touch target
- Padding: Matches existing settings items

### Feedback Snackbar

**When Toggled ON:**
```
┌───────────────────────────────────────────┐
│  Offline mode enabled           [DISMISS] │
└───────────────────────────────────────────┘
```

**When Toggled OFF:**
```
┌───────────────────────────────────────────┐
│  Offline mode disabled          [DISMISS] │
└───────────────────────────────────────────┘
```

## Offline Banner (When Shown in Other Screens)

### No Network Connection
```
┌────────────────────────────────────────────────────┐
│  No internet connection. Some features may be      │
│  unavailable.                          [DISMISS]   │
└────────────────────────────────────────────────────┘
```

### User Forced Offline Mode
```
┌────────────────────────────────────────────────────┐
│  Offline mode is enabled. Network features are     │
│  disabled.                             [DISMISS]   │
└────────────────────────────────────────────────────┘
```

**Banner Characteristics:**
- Type: Material Snackbar (INDEFINITE duration)
- Position: Bottom of screen
- Action: "Dismiss" button
- Auto-dismisses when connectivity returns
- Uses Material Design colors and typography

## Color Scheme

All UI follows existing app theme:
- Primary text: `@color/text_primary`
- Secondary text: `@color/text_secondary`
- Background: `@color/background`
- Switch colors: Material 3 defaults
- Snackbar: Material 3 defaults

## Touch Targets

All interactive elements meet accessibility requirements:
- Switch toggle area: 56dp height (exceeds 48dp minimum)
- Snackbar dismiss button: 48dp+ touch target
- Row clickable area: Full width, 56dp height

## Accessibility

- All text is screenreader compatible
- Switch has proper state announcements
- Clear visual feedback on toggle
- High contrast text (meets WCAG AA)
- Sufficient spacing between elements

## Integration Points

The offline mode toggle integrates seamlessly with existing settings:

1. **Consistent Styling**: Matches existing switch items (Push Notifications, Dark Mode)
2. **Proper Sectioning**: Placed under "Data" section with other data-related settings
3. **Divider Lines**: Uses same hairline dividers as other items
4. **Spacing**: Maintains consistent vertical rhythm (56dp items, 20dp section headers)
5. **Behavior**: Same toggle animation and feedback as other switches

## States

### Toggle States

1. **OFF (Default)**
   - Switch in off position
   - Gray/inactive color
   - Network operations allowed

2. **ON (User Enabled)**
   - Switch in on position  
   - Accent color
   - Network operations blocked
   - Snackbar confirmation shown

### Persistence

The toggle state persists across:
- ✅ App restarts
- ✅ Device reboots
- ✅ App updates
- ✅ Settings screen navigation

Stored in SharedPreferences with key: `user_forced_offline_mode`

## Animation

**Toggle Animation:**
- Standard Material Switch animation (thumb slides, track color changes)
- Duration: ~200ms
- Easing: Material motion curve

**Snackbar Animation:**
- Slides up from bottom
- Duration: ~250ms entrance
- Stays visible until dismissed or connectivity changes
- Slides down on dismiss (~200ms)

## Testing Checklist

Visual/Manual Testing:
- [ ] Toggle appears in correct position
- [ ] Text is readable and properly aligned
- [ ] Switch animates smoothly
- [ ] Snackbar appears with correct message
- [ ] State persists after closing settings
- [ ] State persists after app restart
- [ ] Works in both light and dark themes
- [ ] Accessible with screen reader
- [ ] Touch targets are adequate size
- [ ] No visual glitches or layout shifts

## Screenshots Locations

When testing, take screenshots of:
1. Settings screen with offline mode toggle (light theme)
2. Settings screen with offline mode toggle (dark theme)
3. Toggle in OFF state
4. Toggle in ON state
5. Snackbar "enabled" message
6. Snackbar "disabled" message
7. Offline banner in a content screen (no network)
8. Offline banner in a content screen (user forced)

Save screenshots to: `docs/screenshots/offline_mode/`

## Summary

The offline mode UI addition is:
- ✅ Non-intrusive (fits naturally in settings)
- ✅ Self-explanatory (clear labels and descriptions)
- ✅ Consistent (matches existing design patterns)
- ✅ Accessible (meets standards)
- ✅ Functional (provides clear feedback)
- ✅ Persistent (saves user preference)

The feature enhances the app without cluttering the UI or confusing users.

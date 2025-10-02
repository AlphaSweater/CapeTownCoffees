# ST10269509

# API Key Setup

This project uses the Google Places API key for app functionality.

**How to set up:**
1. The API key is stored in `gradle.properties` as `PLACES_API_KEY`.
2. The build system injects this key into `BuildConfig.PLACES_API_KEY` for use in your code.
3. If you need to change the key, edit the value in `gradle.properties`.

**Example:**
```
PLACES_API_KEY=your_api_key_here
```

**Note:** For this school project, the API key is included in the repository. If you use a production key, restrict it by SHA1 or other means.

---

# Existing README content continues here...


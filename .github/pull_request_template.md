## Summary

<!-- What user problem or invariant does this PR address? -->

## Scope

- 

## Out of scope

- 

## Automated verification

CI is required to run these independent gates:

- [ ] JVM unit tests (`testDebugUnitTest`)
- [ ] Android lint (`lintDebug`)
- [ ] Debug and release assembly (`assembleDebug assembleRelease`)
- [ ] API 34 connected tests (`connectedDebugAndroidTest`)

<!-- Link the workflow run and explain any intentionally not-applicable gate. -->

## Manual verification

<!-- Check only what was actually performed. Use N/A with an explanation instead of implying a pass. -->

- [ ] Primary user flow exercised on a physical device or emulator
- [ ] Portrait and landscape checked where UI changed
- [ ] Large text / compact window checked where layout changed
- [ ] TalkBack or semantics checked where controls changed
- [ ] Offline / interrupted-network behavior checked where data flow changed
- [ ] Process or activity recreation checked where state ownership changed

### Storage and migration changes

- [ ] API 28 private-file behavior checked, or N/A explained
- [ ] API 29 MediaStore and per-item consent checked, or N/A explained
- [ ] API 34 MediaStore and batch consent checked, or N/A explained
- [ ] Existing populated database upgraded without data loss, or N/A explained
- [ ] Restore/cleanup is repeatable and does not select referenced media, or N/A explained

## Documentation

- [ ] Relevant README, CLAUDE guidance, and feature specs match the implemented behavior
- [ ] New or changed migration has an exported Room schema and migration test

## Risk and rollback

<!-- Identify persisted-data, playback, storage, permission, or process-death risks. Explain how this PR can be reverted safely. -->

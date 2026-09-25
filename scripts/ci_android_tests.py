#!/usr/bin/env python3
"""Run the critical CI selection or the complete Android suite, failing on missing evidence."""
from pathlib import Path
import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
# Keep selectors explicit so coverage changes are reviewable; no test implementation is removed.
CRITICAL = (
    'LaunchSmokeTest#instructionsAndWarmupAreAccessible',
    'LaunchSmokeTest#realTimedSessionCompletesAndResultsSurviveRecreationAndBackground',
    'LaunchSmokeTest#pauseWithoutStopInterruptsAndRestartBeginsWarmup',
    'SessionInteractionTest#warmupNeutralFeedbackDuplicateInputAndNewTrial',
    'SessionInteractionTest#holdingDoesNotRespondOrRepeatAndReleaseBelongsToNewTrial',
    'PracticeInteractionTest#practiceExplanationsAndExplicitCompletionActionsStaySeparate',
    'MultiTypeUiTest#independentTogglesPreventEmptySelectionAndOnlyActiveButtonsAppear',
    'MultiTypeUiTest#resultsExposeEachTypeWithoutFabricatingInactiveScores',
    'MultiTypeLifecycleTest#combinedConfigurationSurvivesRecreationAndInterruptsAsOneSession',
    'IntervalLifecycleTest#intervalSnapshotSurvivesRecreationInterruptionAndRestart',
    'LevelSettingsTest#dataStoreRoundTripsMissingInvalidTypesRangesAndCorruption',
    'HistoryCoordinatorTest#everyTerminalEventCapturesOnceAtEveryLevelAndViewModelClearDoesNotCancelSave',
    'HistoryCoordinatorTest#restartAndPlayAgainUseNewIdsPracticeAndPartialSessionsNeverSave',
    'HistoryCoordinatorTest#clearCutoffOrdersPendingWritesAndNeverResurrectsOldHandles',
    'HistoryUiTest#emptyFiltersClearCancelAndGlobalClearRetainDifficulty',
    'RoomHistoryTest#creationReopenOrderingIdenticalRetryConflictAndAtomicClear',
    'RoomHistoryTest#corruptTruncatedEmptyAndIncompatibleStoresArePreservedAcrossEveryRetry',
    'MultiTypeStorageTest#realV1MigrationPreservesEveryFieldAndSupportsNewModesAfterReopen',
    'IntervalStorageTest#v2MigrationPreservesOriginalFieldsAndNewTimelinesRoundTrip',
    'IntervalStorageTest#intervalPreferencesRoundTripAndInvalidFieldDoesNotResetLevelOrTypes',
    'AdsPrivacyTest#consentAndRequestConfigurationAreIndependentAndPermissionGated',
    'AdsPrivacyTest#lateLoadsPrivacyChangesAndDeferredFormsNeverInterruptPlay',
    'LengthStorageTest#v3MigrationPreservesAllLegacyRulesAndNewLengthsRoundTrip',
    'LengthStorageTest#invalidRawLengthsAndOutcomesCannotBeReadOverwrittenOrCleared',
    'SessionExperienceTest#settingsHelpAndVariableCompletionUseSameSnapshotAndSaveOnce',
    'SessionExperienceTest#equalContentRefreshFinishesPreparingEmptyAndPopulatedHistory',
)
PREFIX = 'com.maswadkar.nback.'


def full_inventory():
    """Discover this repository's one-JUnit-class-per-file Kotlin test methods.

    Reject unsupported declarations instead of silently certifying partial coverage.
    """
    expected = []
    for path in sorted((ROOT / 'app/src/androidTest/java').rglob('*.kt')):
        source = path.read_text()
        annotations = re.findall(r'@(?:\w+\.)*Test\b', source)
        if not annotations:
            continue
        package = re.search(r'^package\s+([\w.]+)', source, re.MULTILINE)
        classes = re.findall(r'\bclass\s+(\w+Test)\b', source)
        methods = re.findall(r'@(?:org\.junit\.)?Test\s+fun\s+(\w+)\s*\(', source)
        if not package or classes != [path.stem] or len(methods) != len(annotations):
            raise ValueError(f'Unsupported test declarations: {path}')
        expected.extend(f'{package.group(1)}.{path.stem}#{method}' for method in methods)
    if not expected or len(set(expected)) != len(expected):
        raise ValueError('Full test inventory is empty or contains duplicate methods')
    return expected


def report_signatures(directory):
    return {p: (p.stat().st_mtime_ns, p.stat().st_size) for p in directory.rglob('TEST-*.xml')}


def check_reports(paths, expected):
    passed = set()
    for path in paths:
        for test in ET.parse(path).getroot().iter('testcase'):
            name = test.get('classname', '') + '#' + test.get('name', '')
            if any(test.find(tag) is not None for tag in ('failure', 'error', 'skipped')):
                raise ValueError(f'Test did not pass: {name}')
            passed.add(name)
    missing = set(expected) - passed
    if not passed or missing:
        raise ValueError(f'Missing executed tests: {sorted(missing)}')
    return len(passed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('suite', choices=('critical', 'full'), nargs='?', default='critical')
    args = parser.parse_args()
    try:
        expected = full_inventory() if args.suite == 'full' else [PREFIX + test for test in CRITICAL]
    except ValueError as error:
        print(f'Android CI inventory failed: {error}', file=sys.stderr)
        return 1
    command = [str(ROOT / 'scripts/emulator-test.sh')]
    if args.suite == 'critical':
        command.append('-Pandroid.testInstrumentationRunnerArguments.annotation=' + PREFIX + 'CriticalCi')
    reports = ROOT / 'app/build/outputs/androidTest-results/connected'
    before = report_signatures(reports)
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        return result.returncode
    fresh = [p for p, signature in report_signatures(reports).items() if before.get(p) != signature]
    try:
        count = check_reports(fresh, expected)
    except (ValueError, ET.ParseError) as error:
        print(f'Android CI evidence failed: {error}', file=sys.stderr)
        return 1
    print(f'Android {args.suite} suite: {count} tests passed; all {len(expected)} expected tests executed.')
    return 0


if __name__ == '__main__':
    sys.exit(main())

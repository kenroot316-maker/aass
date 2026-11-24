#!/bin/bash
# Vercel Ignored Build Step (repo-wide, referenced by vercel.json).
#
# This repository is linked to MORE THAN ONE Vercel project (aass-api, sfc-csr).
# Only aass-api should deploy the Neon Rush game backend. This guard skips
# builds for every other project so their live sites are never replaced.
#
# Exit code 1 = BUILD continues.  Exit code 0 = build SKIPPED.
if [ "$VERCEL_PROJECT_NAME" = "aass-api" ] || [ "$VERCEL_PROJECT_ID" = "prj_z6b1BN0D9fat3NQ5Bbj0GKFeOOnL" ]; then
  exit 1
fi
exit 0

#!/bin/bash
set -euo pipefail

BUCKET="${EDU360_S3_BUCKET:-edu360-files}"
REGION="${DEFAULT_REGION:-ap-south-1}"

awslocal s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1 || \
  awslocal s3api create-bucket --bucket "$BUCKET" --region "$REGION"

echo "LocalStack S3 bucket ready: $BUCKET ($REGION)"

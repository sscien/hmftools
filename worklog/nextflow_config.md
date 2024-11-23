
# example for RIS
```
LSF_DOCKER_VOLUMES="/scratch1/fs1/ris:/scratch1/fs1/ris /scratch1/fs1/dinglab:/scratch1/fs1/dinglab" \
thpc-terminal bash -c "cd /scratch1/fs1/dinglab/Active/Projects/ysong/hmftools/test_data/test_output ; nextflow run nf-core/sarek -profile test -c /scratch1/fs1/dinglab/Active/Projects/ysong/hmftools/test_data/test_output/rnaseq.config --outdir /scratch1/fs1/dinglab/Active/Projects/ysong/hmftools/test_data/test_output"
```

# README_ONCOANALYSER.md
nextflow run nf-core/oncoanalyser \
  -profile docker \
  -revision 0.4.5 \
  --mode wgts \
  --genome GRCh38_hmf \
  --input samplesheet.csv \
  --outdir output/

# application on RIS

```
LSF_DOCKER_VOLUMES="/scratch1/fs1/ris:/scratch1/fs1/ris /scratch1/fs1/dinglab:/scratch1/fs1/dinglab" \
thpc-terminal bash -c "cd /scratch1/fs1/dinglab/Active/Projects/ysong/hmftools/test_data/test_output ; nextflow run nf-core/oncoanalyser -profile docker -revision 0.4.5 --mode wgts --genome GRCh38_hmf --input /scratch1/fs1/dinglab/Active/Projects/ysong/hmftools/test_data/input/sample_sheet.csv -c /scratch1/fs1/dinglab/Active/Projects/ysong/hmftools/test_data/test_output/ONCOANALYSER.config --outdir /scratch1/fs1/dinglab/Active/Projects/ysong/hmftools/test_data/test_output"
```


```

rsync -au /storage1/fs1/dinglab/Active/Projects/ysong/Projects/PECGS/Analysis/2024_11_PECGS_tumor_purity/ ysong@katmai.wusm.wustl.edu:/diskmnt/Projects/Users/ysong/project/PECGS/Analysis/

rsync -au /storage1/fs1/dinglab/Active/Projects/ysong/Projects/PECGS/Analysis/2024_11_pecgs_cnv/ ysong@katmai.wusm.wustl.edu:/diskmnt/Projects/Users/ysong/project/PECGS/Analysis/


scp -r y.song@compute1-client-1.ris.wustl.edu:/storage1/fs1/dinglab/Active/Projects/ysong/Projects/PECGS/Analysis/2024_11_pecgs_cnv/  /diskmnt/Projects/Users/ysong/project/PECGS/Analysis/

scp -r y.song@compute1-client-1.ris.wustl.edu:/storage1/fs1/dinglab/Active/Projects/ysong/Projects/PECGS/Analysis/2024_11_PECGS_tumor_purity/  /diskmnt/Projects/Users/ysong/project/PECGS/Analysis/

/storage1/fs1/dinglab/Active/Projects/ysong/Projects/PECGS/Analysis/2024_11_PECGS_tumor_purity
/storage1/fs1/dinglab/Active/Projects/ysong/Projects/PECGS/Analysis/2024_11_pecgs_cnv

```


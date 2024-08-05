package com.hartwig.hmftools.esvee.assembly.phase;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.sv.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.esvee.AssemblyConfig.SV_LOGGER;
import static com.hartwig.hmftools.esvee.AssemblyConstants.ASSEMBLY_REF_BASE_MAX_GAP;
import static com.hartwig.hmftools.esvee.AssemblyConstants.LOCAL_ASSEMBLY_MATCH_DISTANCE;
import static com.hartwig.hmftools.esvee.AssemblyConstants.PROXIMATE_DUP_LENGTH;
import static com.hartwig.hmftools.esvee.AssemblyConstants.REMOTE_REGION_REF_MIN_READS;
import static com.hartwig.hmftools.esvee.assembly.AssemblyUtils.isLocalAssemblyCandidate;
import static com.hartwig.hmftools.esvee.assembly.RefBaseExtender.checkAddRefBaseRead;
import static com.hartwig.hmftools.esvee.assembly.phase.ExtensionType.LOCAL_DEL_DUP;
import static com.hartwig.hmftools.esvee.assembly.phase.ExtensionType.LOCAL_REF_MATCH;
import static com.hartwig.hmftools.esvee.assembly.phase.ExtensionType.REMOTE_REF;
import static com.hartwig.hmftools.esvee.assembly.phase.ExtensionType.SPLIT_LINK;
import static com.hartwig.hmftools.esvee.assembly.phase.ExtensionType.UNMAPPED;
import static com.hartwig.hmftools.esvee.assembly.phase.RemoteRegionAssembler.assemblyOverlapsRemoteRegion;
import static com.hartwig.hmftools.esvee.assembly.read.Read.findMatchingFragmentSupport;
import static com.hartwig.hmftools.esvee.assembly.types.AssemblyOutcome.DUP_BRANCHED;
import static com.hartwig.hmftools.esvee.assembly.types.AssemblyOutcome.LINKED;
import static com.hartwig.hmftools.esvee.assembly.RefBaseExtender.extendRefBases;
import static com.hartwig.hmftools.esvee.assembly.phase.AssemblyLinker.tryAssemblyFacing;
import static com.hartwig.hmftools.esvee.assembly.types.AssemblyOutcome.LOCAL_INDEL;
import static com.hartwig.hmftools.esvee.assembly.types.AssemblyOutcome.REMOTE_REGION;
import static com.hartwig.hmftools.esvee.assembly.types.AssemblyOutcome.SECONDARY;
import static com.hartwig.hmftools.esvee.assembly.types.AssemblyOutcome.UNSET;
import static com.hartwig.hmftools.esvee.assembly.types.SupportRead.hasFragmentOtherRead;
import static com.hartwig.hmftools.esvee.assembly.types.SupportRead.hasMatchingFragmentRead;
import static com.hartwig.hmftools.esvee.assembly.types.SupportType.DISCORDANT;
import static com.hartwig.hmftools.esvee.assembly.types.SupportType.EXTENSION;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeInterface;
import com.hartwig.hmftools.esvee.assembly.RefBaseExtender;
import com.hartwig.hmftools.esvee.assembly.UnmappedBaseExtender;
import com.hartwig.hmftools.esvee.assembly.read.Read;
import com.hartwig.hmftools.esvee.assembly.types.AssemblyLink;
import com.hartwig.hmftools.esvee.assembly.types.SupportRead;
import com.hartwig.hmftools.esvee.assembly.types.JunctionAssembly;
import com.hartwig.hmftools.esvee.assembly.types.LinkType;
import com.hartwig.hmftools.esvee.assembly.types.PhaseGroup;
import com.hartwig.hmftools.esvee.assembly.types.PhaseSet;
import com.hartwig.hmftools.esvee.assembly.types.RemoteRegion;
import com.hartwig.hmftools.esvee.common.CommonUtils;

public class PhaseSetBuilder
{
    private final PhaseGroup mPhaseGroup;
    private final RefGenomeInterface mRefGenome;
    private final RemoteRegionAssembler mRemoteRegionAssembler;
    private final LocalSequenceMatcher mLocalSequenceMatcher;

    // references from phase group
    private final List<JunctionAssembly> mAssemblies;
    private final List<PhaseSet> mPhaseSets; // final proposed phase sets
    private final List<AssemblyLink> mSecondarySplitLinks;
    private final List<ExtensionCandidate> mExtensionCandidates;

    // working cache only
    private final List<AssemblyLink> mSplitLinks;
    private final List<AssemblyLink> mFacingLinks;

    public PhaseSetBuilder(
            final RefGenomeInterface refGenome, final RemoteRegionAssembler remoteRegionAssembler, final PhaseGroup phaseGroup)
    {
        mRefGenome = refGenome;
        mPhaseGroup = phaseGroup;
        mRemoteRegionAssembler = remoteRegionAssembler;
        mLocalSequenceMatcher = new LocalSequenceMatcher(refGenome, LOCAL_ASSEMBLY_MATCH_DISTANCE);

        mPhaseSets = mPhaseGroup.phaseSets();
        mAssemblies = mPhaseGroup.assemblies();
        mSecondarySplitLinks = mPhaseGroup.secondaryLinks();

        mExtensionCandidates = Lists.newArrayList();
        mSplitLinks = Lists.newArrayList();
        mFacingLinks = Lists.newArrayList();
    }

    public void buildPhaseSets()
    {
        // findCandidateExtensions();

        formSplitLinks();

        findRemoteRefAssemblies();

        buildUnmappedExtensions();

        addUnlinkedAssemblyRefSupport();

        formFacingLinks();

        formPhaseSets();

        addChainedSupport();

        cleanupAssemblies();
    }

    private void findCandidateExtensions()
    {
        findUnmappedExtensions();
        findSplitLinkCandidates();
        findRemoteRefCandidates();
    }

    private void findUnmappedExtensions()
    {
        // any assembly not in a link uses unmapped reads to extend out the extension sequence
        for(JunctionAssembly assembly : mAssemblies)
        {
            if(assembly.unmappedReads().isEmpty())
                continue;

            UnmappedBaseExtender unmappedBaseExtender = new UnmappedBaseExtender(assembly);
            unmappedBaseExtender.processReads(assembly.unmappedReads());

            if(!unmappedBaseExtender.supportReads().isEmpty())
            {
                mExtensionCandidates.add(new ExtensionCandidate(
                        UNMAPPED, assembly, format("readSpan(%d)", unmappedBaseExtender.extensionBases().length),
                                unmappedBaseExtender.supportReads().size()));
            }
        }
    }

    private void findSplitLinkCandidates()
    {
        for(int i = 0; i < mAssemblies.size(); ++i)
        {
            JunctionAssembly assembly1 = mAssemblies.get(i);

            Set<JunctionAssembly> linkedAssemblies = Sets.newHashSet(assembly1);

            for(int j = i + 1; j < mAssemblies.size(); ++j)
            {
                JunctionAssembly assembly2 = mAssemblies.get(j);

                if(assembly1.indel() != assembly2.indel())
                    continue;

                boolean isLocalLink = isLocalAssemblyCandidate(assembly1, assembly2);

                Set<String> firstSupportReadIds = assembly1.support().stream().map(x -> x.fullReadId()).collect(Collectors.toSet());
                Set<String> firstCandidateReadIds = assembly1.candidateSupport().stream().map(x -> x.id()).collect(Collectors.toSet());

                int assembly1MatchedSupport = 0;
                int assembly2MatchedSupport = 0;
                int assembly1CandidateReads = 0;
                int assembly2CandidateReads = 0;

                for(SupportRead support : assembly2.support())
                {
                    if(firstSupportReadIds.contains(support.fullReadId()))
                    {
                        firstSupportReadIds.remove(support.fullReadId());
                        ++assembly1MatchedSupport;
                        ++assembly2MatchedSupport;
                    }

                    if(firstCandidateReadIds.contains(support.fullReadId()))
                    {
                        firstCandidateReadIds.remove(support.fullReadId());
                        ++assembly1CandidateReads;
                        ++assembly2MatchedSupport;
                    }
                }

                for(Read read : assembly2.candidateSupport())
                {
                    if(firstSupportReadIds.contains(read.id()))
                    {
                        firstSupportReadIds.remove(read.id());
                        ++assembly1MatchedSupport;
                        ++assembly2CandidateReads;
                    }

                    if(firstCandidateReadIds.contains(read.id()))
                    {
                        firstCandidateReadIds.remove(read.id());
                        ++assembly1CandidateReads;
                        ++assembly2CandidateReads;
                    }
                }

                AssemblyLink assemblyLink = checkSplitLink(assembly1, assembly2);

                if(assemblyLink == null)
                    continue;

                ExtensionType type = isLocalLink ? LOCAL_DEL_DUP : SPLIT_LINK;

                mExtensionCandidates.add(new ExtensionCandidate(
                        type, assemblyLink, assembly1MatchedSupport, assembly1CandidateReads,
                        assembly2MatchedSupport, assembly2CandidateReads, ""));

                linkedAssemblies.add(assembly1);
                linkedAssemblies.add(assembly2);
            }

            if(true | !linkedAssemblies.contains(assembly1))
            {
                // check local links
                AssemblyLink localRefLink = mLocalSequenceMatcher.tryLocalAssemblyLink(assembly1);

                if(localRefLink != null)
                {
                    mExtensionCandidates.add(new ExtensionCandidate(
                            LOCAL_REF_MATCH, localRefLink, assembly1.supportCount(), 0,
                            0, 0, ""));
                }
            }
        }
    }

    private void findRemoteRefCandidates()
    {
        for(JunctionAssembly assembly : mAssemblies)
        {
            if(!RemoteRegionAssembler.isExtensionCandidateAssembly(assembly))
                continue;

            // collect remote regions which aren't only supplementaries nor which overlap another phase assembly
            List<RemoteRegion> remoteRegions = assembly.remoteRegions().stream()
                    .filter(x -> !x.isSuppOnlyRegion())
                    .filter(x -> x.readIds().size() >= REMOTE_REGION_REF_MIN_READS)
                    .filter(x -> mAssemblies.stream().filter(y -> y != assembly).noneMatch(y -> assemblyOverlapsRemoteRegion(y, x)))
                    .collect(Collectors.toList());

            if(remoteRegions.isEmpty())
                continue;

            // evaluate by remote regions with most linked reads
            Collections.sort(remoteRegions, Comparator.comparingInt(x -> -x.nonSuppReadCount()));

            for(RemoteRegion remoteRegion : remoteRegions)
            {
                Set<String> localReadIds = assembly.support().stream()
                        .filter(x -> remoteRegion.readIds().contains(x.id()))
                        .map(x -> x.fullReadId())
                        .collect(Collectors.toSet());

                int supportCount = localReadIds.size();

                assembly.candidateSupport().stream()
                        .filter(x -> !x.hasJunctionMate())
                        .filter(x -> remoteRegion.containsReadId(x.id()))
                        .forEach(x -> localReadIds.add(x.id()));

                int candidateCount = localReadIds.size() - supportCount;

                if(localReadIds.size() < REMOTE_REGION_REF_MIN_READS)
                    continue;

                AssemblyLink assemblyLink = mRemoteRegionAssembler.tryRemoteAssemblyLink(assembly, remoteRegion, localReadIds);

                if(assemblyLink == null)
                    continue;

                JunctionAssembly remoteAssembly = assemblyLink.otherAssembly(assembly);

                mExtensionCandidates.add(new ExtensionCandidate(
                        REMOTE_REF, assemblyLink, supportCount, candidateCount, 0, 0,
                        format("readSpan(%d)", remoteAssembly.refBaseLength())));
            }
        }
    }

    private boolean formsLocalLink(final JunctionAssembly assembly)
    {
        AssemblyLink localRefLink = mLocalSequenceMatcher.tryLocalAssemblyLink(assembly);

        if(localRefLink == null)
            return false;

        assembly.setOutcome(LOCAL_INDEL);

        JunctionAssembly localRefAssembly = localRefLink.otherAssembly(assembly);
        localRefAssembly.setOutcome(LOCAL_INDEL);

        mPhaseGroup.addDerivedAssembly(localRefAssembly);
        mSplitLinks.add(localRefLink);

        // no need to build out the link with matching reads etc since only one assembly has read support
        return true;
    }

    private void formSplitLinkFromPair()
    {
        // simpler routine without prioritising pairs, facing links or branching
        JunctionAssembly assembly1 = mAssemblies.get(0);
        JunctionAssembly assembly2 = mAssemblies.get(1);

        if(assembly1.indel() != assembly2.indel())
            return;

        boolean isLocalLink = isLocalAssemblyCandidate(assembly1, assembly2);

        if(!isLocalLink)
        {
            // first check individual local alignments
            boolean matchesLocal = formsLocalLink(assembly1);
            matchesLocal = formsLocalLink(assembly2) || matchesLocal;

            if(matchesLocal)
                return;
        }

        boolean hasSharedFragments = assembly1.support().stream().anyMatch(x -> hasMatchingFragmentRead(assembly2.support(), x));

        if(!hasSharedFragments)
        {
            hasSharedFragments = assembly1.candidateSupport().stream().anyMatch(x -> hasMatchingFragmentRead(assembly2.support(), x));
        }

        if(!hasSharedFragments)
        {
            hasSharedFragments = assembly2.candidateSupport().stream().anyMatch(x -> hasMatchingFragmentRead(assembly1.support(), x));
        }

        if(hasSharedFragments)
        {
            AssemblyLink assemblyLink = checkSplitLink(assembly1, assembly2);

            if(assemblyLink != null && applySplitLinkSupport(assembly1, assembly2, false))
            {
                // bypass the phase set building routine by forming one directly
                mPhaseSets.add(new PhaseSet(assemblyLink));

            }
        }
    }

    private void formSplitLinks()
    {
        if(mAssemblies.size() == 1)
        {
            formsLocalLink(mAssemblies.get(0));
            return;
        }

        if(mAssemblies.size() == 2)
        {
            formSplitLinkFromPair();
            return;
        }

        // where there are more than 2 assemblies, start with the ones with the most support and overlapping junction reads
        List<SharedAssemblySupport> assemblySupportPairs = Lists.newArrayList();

        for(int i = 0; i < mAssemblies.size() - 1; ++i)
        {
            JunctionAssembly assembly1 = mAssemblies.get(i);

            for(int j = i + 1; j < mAssemblies.size(); ++j)
            {
                JunctionAssembly assembly2 = mAssemblies.get(j);

                if(assembly1.indel() != assembly2.indel())
                    continue;

                boolean isLocalLink = isLocalAssemblyCandidate(assembly1, assembly2);

                Set<String> firstReadIds = Sets.newHashSet();

                for(SupportRead support : assembly1.support())
                {
                    firstReadIds.add(support.fullReadId());
                }

                // also check candidate reads
                for(Read read : assembly1.candidateSupport())
                {
                    firstReadIds.add(read.id());
                }

                int sharedCount = 0;

                for(SupportRead support : assembly2.support())
                {
                    if(firstReadIds.contains(support.fullReadId()))
                    {
                        firstReadIds.remove(support.fullReadId());
                        ++sharedCount;
                    }
                }

                for(Read read : assembly2.candidateSupport())
                {
                    if(firstReadIds.contains(read.id()))
                    {
                        firstReadIds.remove(read.id());
                        ++sharedCount;
                    }
                }

                int candidateOnlyCount = 0;

                /*
                for(SupportRead support : assembly1.candidateSupport())
                {
                    if(hasMatchingFragment(assembly2.candidateSupport(), support))
                        ++candidateOnlyCount;
                }
                */

                if(sharedCount > 0 || isLocalLink)
                    assemblySupportPairs.add(new SharedAssemblySupport(assembly1, assembly2, sharedCount, candidateOnlyCount, isLocalLink));
            }
        }

        // favour local DELs & DUPs then pairs with the most support
        Collections.sort(assemblySupportPairs);

        // build any split links and only allow an assembly to be used once
        Set<JunctionAssembly> linkedAssemblies = Sets.newHashSet();

        while(!assemblySupportPairs.isEmpty())
        {
            SharedAssemblySupport sharedReadPair = assemblySupportPairs.remove(0);

            JunctionAssembly assembly1 = sharedReadPair.Assembly1;
            JunctionAssembly assembly2 = sharedReadPair.Assembly2;

            boolean assembly1Linked = linkedAssemblies.contains(assembly1);
            boolean assembly2Linked = linkedAssemblies.contains(assembly2);

            if(assembly1Linked && assembly2Linked)
                continue;

            // test if a link can be made
            if(!assembly1Linked && !assembly2Linked)
            {
                // first check if either assembly matches a local sequence (note both are checked)
                if(!sharedReadPair.IsLocalDelDup)
                {
                    boolean matchesLocal = false;

                    if(formsLocalLink(assembly1))
                    {
                        matchesLocal = true;
                        linkedAssemblies.add(assembly1);
                    }

                    if(formsLocalLink(assembly2))
                    {
                        matchesLocal = true;
                        linkedAssemblies.add(assembly2);
                    }

                    if(matchesLocal)
                        continue;
                }

                AssemblyLink assemblyLink = checkSplitLink(assembly1, assembly2);

                if(assemblyLink == null)
                    continue;

                boolean allowBranching = !(assemblyLink.svType() == DUP && assemblyLink.length() < PROXIMATE_DUP_LENGTH);

                if(applySplitLinkSupport(assembly1, assembly2, allowBranching))
                {
                    mSplitLinks.add(assemblyLink);
                    linkedAssemblies.add(assembly1);
                    linkedAssemblies.add(assembly2);
                }

                continue;
            }

            // form secondaries for any assemblies which aren't duplicates of another or supplementary only
            if(assembly1.outcome().isDuplicate() || assembly2.outcome().isDuplicate())
                continue;

            boolean allowSequenceMismatches = true; // was false for secondaries
            AssemblyLink assemblyLink = AssemblyLinker.tryAssemblyOverlap(assembly1, assembly2, allowSequenceMismatches);

            if(assemblyLink != null && applySplitLinkSupport(assembly1, assembly2, false))
            {
                mSecondarySplitLinks.add(assemblyLink);

                if(!assembly1Linked)
                    assembly1.setOutcome(SECONDARY);
                else
                    assembly2.setOutcome(SECONDARY);
            }
        }
    }

    private class SharedAssemblySupport implements Comparable<SharedAssemblySupport>
    {
        public final JunctionAssembly Assembly1;
        public final JunctionAssembly Assembly2;
        public final int SharedSupport;
        public final int CandidateOnlySupport;
        public final boolean IsLocalDelDup;

        public SharedAssemblySupport(
                final JunctionAssembly assembly1, final JunctionAssembly assembly2, int sharedSupport, int candidateOnlySupport,
                final boolean localDelDup)
        {
            Assembly1 = assembly1;
            Assembly2 = assembly2;
            SharedSupport = sharedSupport;
            CandidateOnlySupport = candidateOnlySupport;
            IsLocalDelDup = localDelDup;
        }

        @Override
        public int compareTo(final SharedAssemblySupport other)
        {
            if(other.IsLocalDelDup != IsLocalDelDup)
                return IsLocalDelDup ? -1 : 1;

            if(SharedSupport != other.SharedSupport)
                return SharedSupport > other.SharedSupport ? -1 : 1;

            return 0;
        }

        public String toString()
        {
            return format("%s + %s shared(%d) candidates(%d) %s",
                    Assembly1.junction().coords(), Assembly2.junction().coords(), SharedSupport, CandidateOnlySupport,
                    IsLocalDelDup ? "localDelDup" : "");
        }
    }

    private AssemblyLink checkSplitLink(final JunctionAssembly assembly1, final JunctionAssembly assembly2)
    {
        if(assembly1.junction() == assembly2.junction()) // ignore duplicates
            return null;

        // handle local INDELs here since the following logic currently applies to them
        AssemblyLink assemblyLink = AssemblyLinker.tryAssemblyIndel(assembly1, assembly2);

        if(assemblyLink != null)
            return assemblyLink;

        return AssemblyLinker.tryAssemblyOverlap(assembly1, assembly2);
    }

    private void findRemoteRefAssemblies()
    {
        // form a fixed initial list since remote assemblies may be added to the phase group
        List<JunctionAssembly> initialAssemblies = Lists.newArrayList(mAssemblies);

        for(JunctionAssembly assembly : initialAssemblies)
        {
            if(!RemoteRegionAssembler.isExtensionCandidateAssembly(assembly))
                continue;

            boolean alreadyLinked = mSplitLinks.stream().anyMatch(x -> x.hasAssembly(assembly));

            boolean foundRemoteLink = false;

            // collect remote regions which aren't only supplementaries nor which overlap another phase assembly
            List<RemoteRegion> remoteRegions = assembly.remoteRegions().stream()
                    .filter(x -> !x.isSuppOnlyRegion())
                    .filter(x -> x.readIds().size() >= REMOTE_REGION_REF_MIN_READS)
                    .filter(x -> mAssemblies.stream().filter(y -> y != assembly).noneMatch(y -> assemblyOverlapsRemoteRegion(y, x)))
                    .collect(Collectors.toList());

            if(remoteRegions.isEmpty())
                continue;

            // evaluate by remote regions with most linked reads
            Collections.sort(remoteRegions, Comparator.comparingInt(x -> -x.nonSuppReadCount()));

            for(RemoteRegion remoteRegion : remoteRegions)
            {
                Set<String> localReadIds = assembly.support().stream()
                        .filter(x -> remoteRegion.readIds().contains(x.id()))
                        .map(x -> x.fullReadId())
                        .collect(Collectors.toSet());

                assembly.candidateSupport().stream()
                        .filter(x -> !x.hasJunctionMate())
                        .filter(x -> remoteRegion.containsReadId(x.id()))
                        .forEach(x -> localReadIds.add(x.id()));

                if(localReadIds.size() < REMOTE_REGION_REF_MIN_READS)
                    continue;

                AssemblyLink assemblyLink = mRemoteRegionAssembler.tryRemoteAssemblyLink(assembly, remoteRegion, localReadIds);

                if(assemblyLink == null)
                    continue;

                JunctionAssembly remoteAssembly = assemblyLink.otherAssembly(assembly);

                remoteAssembly.setOutcome(REMOTE_REGION);
                mPhaseGroup.addDerivedAssembly(remoteAssembly);

                if(!foundRemoteLink && !alreadyLinked)
                {
                    // only form one remote link for each assembly
                    if(applySplitLinkSupport(assembly, remoteAssembly, true))
                    {
                        assembly.setOutcome(REMOTE_REGION);
                        foundRemoteLink = true;
                        mSplitLinks.add(assemblyLink);
                    }
                }
                else
                {
                    if(applySplitLinkSupport(assembly, remoteAssembly, false))
                        mSecondarySplitLinks.add(assemblyLink);
                }
            }
        }
    }

    private void buildUnmappedExtensions()
    {
        // any assembly not in a link uses unmapped reads to extend out the extension sequence
        for(JunctionAssembly assembly : mAssemblies)
        {
            // ignore if linked to a valid junction assembly
            if(assembly.outcome() == LOCAL_INDEL || assembly.outcome() == LINKED || assembly.outcome() == DUP_BRANCHED)
                continue;

            if(assembly.unmappedReads().isEmpty())
                continue;

            UnmappedBaseExtender unmappedBaseExtender = new UnmappedBaseExtender(assembly);
            unmappedBaseExtender.processReads(assembly.unmappedReads());

            if(!unmappedBaseExtender.supportReads().isEmpty())
            {
                SV_LOGGER.trace("assembly({}) extended {} -> {} with {} unmapped reads",
                        assembly, assembly.extensionLength(), unmappedBaseExtender.extensionBaseLength(),
                        unmappedBaseExtender.supportReads().size());

                assembly.expandExtensionBases(
                        unmappedBaseExtender.extensionBases(), unmappedBaseExtender.baseQualities(), unmappedBaseExtender.supportReads());
            }

            assembly.unmappedReads().clear(); // no longer required
        }
    }

    private void addUnlinkedAssemblyRefSupport()
    {
        // any assembly which did not form a link or only an unmapped extension will now extend its ref bases from junction & extension mates
        for(JunctionAssembly assembly : mAssemblies)
        {
            if(assembly.outcome() != UNSET)
                continue;
            
            if(mSplitLinks.stream().anyMatch(x -> x.hasAssembly(assembly)))
                continue;

            // add junction mate reads to the ref side bases
            List<Read> refExtensionReads = Lists.newArrayList();

            List<SupportRead> extensionSupport = assembly.support().stream().filter(x -> x.type() == EXTENSION).collect(Collectors.toList());

            for(Read read : assembly.candidateSupport())
            {
                if(read.hasJunctionMate())
                {
                    refExtensionReads.add(read);
                }
                else
                {
                    if(extensionSupport.stream().anyMatch(x -> x.matchesFragment(read, false)))
                        refExtensionReads.add(read);
                }
            }

            extendRefBases(assembly, refExtensionReads, mRefGenome, false);
        }
    }

    private boolean applySplitLinkSupport(final JunctionAssembly assembly1, final JunctionAssembly assembly2, boolean allowBranching)
    {
        // look for shared reads between the assemblies, and factor in discordant reads which were only considered candidates until now
        List<Read> matchedCandidates1 = Lists.newArrayList();
        List<Read> matchedCandidates2 = Lists.newArrayList();

        checkMatchingCandidateSupport(assembly2, assembly1.candidateSupport(), assembly2.candidateSupport(), matchedCandidates1, matchedCandidates2);
        checkMatchingCandidateSupport(assembly1, assembly2.candidateSupport(), Collections.emptyList(), matchedCandidates2, matchedCandidates1);

        // remove any ref discordant candidates if their only criteria for inclusion is being long
        List<Read> refCandidates1 = Lists.newArrayList();
        boolean hasNonLocalTumorFragment = false;
        boolean hasNonLocalRefFragment = false;

        for(Read read : matchedCandidates1)
        {
            if(read.isReference())
            {
                refCandidates1.add(read);

                hasNonLocalRefFragment |= CommonUtils.isDiscordantFragment(
                        read.bamRecord(), -1, read.supplementaryData());
            }
            else
            {
                hasNonLocalTumorFragment |= CommonUtils.isDiscordantFragment(
                        read.bamRecord(), -1, read.supplementaryData());
            }
        }

        if(hasNonLocalTumorFragment && !hasNonLocalRefFragment)
        {
            List<Read> refCandidates2 = matchedCandidates2.stream().filter(x -> x.isReference()).collect(Collectors.toList());
            refCandidates1.forEach(x -> matchedCandidates1.remove(x));
            refCandidates2.forEach(x -> matchedCandidates2.remove(x));
        }

        /*
        if(matchedCandidates1.isEmpty() || matchedCandidates2.isEmpty())
            return false;
        */

        // build out ref-base assembly support from these non-junction reads - both matched discordant and junction mates
        extendRefBases(assembly1, matchedCandidates1, mRefGenome, allowBranching);
        extendRefBases(assembly2, matchedCandidates2, mRefGenome, allowBranching);

        if(assembly1.outcome() == UNSET)
            assembly1.setOutcome(LINKED);

        if(assembly2.outcome() == UNSET)
            assembly2.setOutcome(LINKED);

        return true;
    }

    private static void checkMatchingCandidateSupport(
            final JunctionAssembly otherAssembly,
            final List<Read> candidateSupport, final List<Read> otherCandidateSupport,
            final List<Read> matchedCandidates, final List<Read> otherMatchedCandidates)
    {
        // consider each candidate support read to see if it has a matching read in the other assembly's candidates or junction reads
        int index = 0;
        while(index < candidateSupport.size())
        {
            Read candidateRead = candidateSupport.get(index);

            if(candidateRead.hasJunctionMate()) // added automatically to extend the reference
            {
                candidateSupport.remove(index);
                matchedCandidates.add(candidateRead);
                continue;
            }

            // first check for discordant reads with matching support in the other assembly
            if(hasFragmentOtherRead(otherAssembly.support(), candidateRead))
            {
                candidateSupport.remove(index);
                matchedCandidates.add(candidateRead);
                continue;
            }

            // then check for candidate & candidate matches
            if(!otherCandidateSupport.isEmpty())
            {
                List<Read> matchedCandidateSupport = findMatchingFragmentSupport(otherCandidateSupport, candidateRead);

                if(!matchedCandidateSupport.isEmpty())
                {
                    candidateSupport.remove(index);
                    matchedCandidates.add(candidateRead);

                    // remove from other's candidates to avoid checking again
                    matchedCandidateSupport.forEach(x -> otherCandidateSupport.remove(x));
                    otherMatchedCandidates.addAll(matchedCandidateSupport);

                    continue;
                }
            }

            ++index;
        }
    }

    private void formFacingLinks()
    {
        if(mAssemblies.size() == 1 || (mAssemblies.size() == 2 && mSplitLinks.size() == 1))
            return;

        // for each assembly in a split link, look for a facing link (whether linked or not)
        Set<JunctionAssembly> facingAssemblies = Sets.newHashSet();

        for(int i = 0; i < mAssemblies.size() - 1; ++i)
        {
            JunctionAssembly assembly1 = mAssemblies.get(i);

            for(int j = i + 1; j < mAssemblies.size(); ++j)
            {
                JunctionAssembly assembly2 = mAssemblies.get(j);

                if(facingAssemblies.contains(assembly1) || facingAssemblies.contains(assembly2))
                    continue;

                AssemblyLink facingLink = tryAssemblyFacing(assembly1, assembly2);

                if(facingLink == null)
                    continue;

                // compelling evidence is a read from the new assembly which overlaps with the linked junction's reads
                // if(assembliesShareReads(assembly2, splitAssembly))
                mFacingLinks.add(facingLink);
                facingAssemblies.add(assembly1);
                facingAssemblies.add(assembly2);
            }
        }
    }

    private void formPhaseSets()
    {
        // use split and facing links to assign assemblies to phase sets
        while(!mSplitLinks.isEmpty() || !mFacingLinks.isEmpty())
        {
            AssemblyLink assemblyLink = !mSplitLinks.isEmpty() ? mSplitLinks.remove(0) : mFacingLinks.remove(0);

            PhaseSet phaseSet = new PhaseSet(assemblyLink);
            mPhaseSets.add(phaseSet);

            // look for facing and then splits links for this phase set
            for(int se = SE_START; se <= SE_END; ++se)
            {
                // check start and then end links of this phase set
                JunctionAssembly linkingAssembly = (se == SE_START) ? assemblyLink.first() : assemblyLink.second();
                boolean findSplit = assemblyLink.type() == LinkType.FACING;

                while(true)
                {
                    AssemblyLink nextLink = findLinkedAssembly(linkingAssembly, findSplit);

                    if(nextLink == null)
                        break;

                    if(se == SE_START)
                        phaseSet.addAssemblyLinkStart(nextLink);
                    else
                        phaseSet.addAssemblyLinkEnd(nextLink);

                    findSplit = !findSplit;
                    linkingAssembly = nextLink.otherAssembly(linkingAssembly);
                }
            }
        }

        for(PhaseSet phaseSet : mPhaseSets)
        {
            // gather in secondaries
            for(AssemblyLink link : mSecondarySplitLinks)
            {
                if(phaseSet.hasAssembly(link.first()) || phaseSet.hasAssembly(link.second()))
                    phaseSet.addSecondaryLinkEnd(link);
            }
        }
    }

    private AssemblyLink findLinkedAssembly(final JunctionAssembly assembly, boolean findSplit)
    {
        // find a link using one assembly of a particular type, then remove it from future consideration
        List<AssemblyLink> searchLinks = findSplit ? mSplitLinks : mFacingLinks;

        int index = 0;
        while(index < searchLinks.size())
        {
            AssemblyLink link = searchLinks.get(index);

            if(link.hasAssembly(assembly))
            {
                searchLinks.remove(index);

                if(!findSplit)
                {
                    // remove any other facing links which use this assembly
                    JunctionAssembly otherAssembly = link.otherAssembly(assembly);

                    int otherIndex = 0;
                    while(otherIndex < mFacingLinks.size())
                    {
                        AssemblyLink otherLink = searchLinks.get(otherIndex);
                        if(otherLink.hasAssembly(assembly) || otherLink.hasAssembly(otherAssembly))
                            searchLinks.remove(otherLink);
                        else
                            ++otherIndex;
                    }
                }

                return link;
            }

            ++index;
        }

        return null;
    }

    private void addChainedSupport()
    {
        if(mPhaseSets.isEmpty())
            return;

        // look for matched candidate reads spanning proximate breakends and add as support
        for(PhaseSet phaseSet : mPhaseSets)
        {
            if(phaseSet.assemblies().size() <= 2)
                continue;

            for(int i = 0; i < phaseSet.assemblies().size(); ++i)
            {
                JunctionAssembly assembly1 = mAssemblies.get(i);

                List<AssemblyLink> assemblyLinks = phaseSet.findAssemblyLinks(assembly1);

                for(int j = i + 1; j < phaseSet.assemblies().size(); ++j)
                {
                    JunctionAssembly assembly2 = mAssemblies.get(j);

                    // ignore already linked assemblies since their support has been matched, and ignore assemblies in a facing link
                    if(assemblyLinks.stream().anyMatch(x -> x.hasAssembly(assembly2)))
                        continue;

                    addMatchingCandidateSupport(phaseSet, assembly1, assembly2);
                }
            }
        }
    }

    private static void addMatchingCandidateSupport(
            final PhaseSet phaseSet, final JunctionAssembly assembly1, final JunctionAssembly assembly2)
    {
        // assemblies must face each other in the chain
        if(!phaseSet.assembliesFaceInPhaseSet(assembly1, assembly2))
            return;

        for(int i = 0; i <= 1; ++i)
        {
            final JunctionAssembly assembly = (i == 0) ? assembly1 : assembly2;
            final JunctionAssembly otherAssembly = (i == 0) ? assembly2 : assembly1;

            int index = 0;

            while(index < assembly.candidateSupport().size())
            {
                Read candidateRead = assembly.candidateSupport().get(index);

                if(candidateRead.hasJunctionMate())
                {
                    ++index;
                    continue;
                }

                // only link reads into a supporting fragment across chain links if they face towards each other in the chain
                SupportRead matchedRead = otherAssembly.support().stream()
                        .filter(x -> x.matchesFragment(candidateRead, false)).findFirst().orElse(null);

                if(matchedRead != null)
                {
                    assembly.candidateSupport().remove(index);
                    checkAddRefBaseRead(assembly, candidateRead, DISCORDANT);
                    continue;
                }

                // otherwise check for candidate matches
                if(i == 0)
                {
                    Read matchedCandidate = otherAssembly.candidateSupport().stream()
                            .filter(x -> x.matchesFragment(candidateRead, false)).findFirst().orElse(null);

                    if(matchedCandidate != null)
                    {
                        assembly.candidateSupport().remove(index);
                        checkAddRefBaseRead(assembly, candidateRead, DISCORDANT);

                        otherAssembly.candidateSupport().remove(matchedCandidate);
                        checkAddRefBaseRead(otherAssembly, matchedCandidate, DISCORDANT);

                        continue;
                    }
                }

                ++index;
            }
        }
    }

    private void cleanupAssemblies()
    {
        List<JunctionAssembly> branchedAssembliesToRemove = null;

        for(JunctionAssembly assembly : mAssemblies)
        {
            assembly.clearCandidateSupport(); // no further use for candidate reads

            assembly.clearSupportCachedReads(); // remove references to actual SAMRecords, keeping only summary info

            boolean inPhaseSet = mPhaseSets.stream().anyMatch(x -> x.hasAssembly(assembly));

            if(inPhaseSet && assembly.outcome() == UNSET)
                assembly.setOutcome(LINKED);

            RefBaseExtender.trimAssemblyRefBases(assembly, ASSEMBLY_REF_BASE_MAX_GAP);

            if(assembly.outcome() == DUP_BRANCHED)
            {
                // remove any branched assemblies which did not form a facing link
                boolean inFacingLink = false;

                if(inPhaseSet)
                {
                    for(PhaseSet phaseSet : mPhaseSets)
                    {
                        if(phaseSet.assemblyLinks().stream().filter(x -> x.type() == LinkType.FACING).anyMatch(x -> x.hasAssembly(assembly)))
                        {
                            // set outcome to original assembly
                            JunctionAssembly originalAssembly = mAssemblies.stream()
                                    .filter(x -> x != assembly)
                                    .filter(x -> x.junction().compareTo(assembly.junction()) == 0).findFirst().orElse(null);

                            if(originalAssembly != null)
                                assembly.setOutcome(originalAssembly.outcome());

                            inFacingLink = true;
                            break;
                        }
                    }
                }

                if(!inFacingLink)
                {
                    if(branchedAssembliesToRemove == null)
                        branchedAssembliesToRemove = Lists.newArrayList(assembly);
                    else
                        branchedAssembliesToRemove.add(assembly);
                }
            }
        }

        // finally remove any branched assemblies which did not form a facing link
        if(branchedAssembliesToRemove != null)
        {
            for(JunctionAssembly branchedAssembly : branchedAssembliesToRemove)
            {
                mPhaseGroup.assemblies().remove(branchedAssembly);
                mPhaseGroup.derivedAssemblies().remove(branchedAssembly);
            }
        }
    }
}

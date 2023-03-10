package com.laseryi.buildgraph

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

abstract class BaseController implements Serializable {

    static String primaryLabel = 'BuildGraph'

    abstract void runPipeline()

    PipelineConfiguration config
    Map graph = [:]

    List finishedNodes = []
    List failedNodes = []

    List pendingBadges = []

    boolean parallel = false

    // Jenkins stuffs
    Script steps = null

    protected BaseController(PipelineConfiguration config, Script steps) {
        this.config = config
        this.steps = steps

        this.graph = [:]
    }

    void initialWorkspace() {
        if (this.config.nocheckout) {
            return
        }

        Map result = setupWorkspace(true)

        if (result.containsKey('P4_CHANGELIST')) {
            this.config.changelist = result['P4_CHANGELIST']
        } else if (result.containsKey('GIT_COMMIT')) {
            this.config.changelist = result['GIT_COMMIT']
        } else {
            steps.error('Unknown scm provider')
        }
    }

    Map setupWorkspace(boolean initial = false) {
        steps.echo('Cleanup build graph local saved directory')
        // We really don't care about the delete result
        steps.bat(
            returnStatus: true,
            script: 'rmdir /s /q \"Engine/Saved/BuildGraph\"'
        )

        if (this.config.nocheckout) {
            return [:]
        }

        String branch = this.config.branch
        String changelist = this.config.changelist
        String executor = steps.env.EXECUTOR_NUMBER

        Object scm = this.config.scm(branch, changelist, executor)
        assert scm != null

        return steps.checkout(scm: scm, changelog: initial, poll: initial)
    }

    void initialBadgeStatus() {
        if (this.config.preview) {
            return
        }

        if (!this.graph.containsKey('Badges')) {
            return
        }

        List badges = this.graph['Badges']

        List finishedBadges = postBadgeStatus(badges, true)

        this.pendingBadges = finishedBadges
    }

    void updateBadgeStatus() {
        if (this.config.preview) {
            return
        }

        List finishedBadges = postBadgeStatus(this.pendingBadges, false)

        this.pendingBadges = this.pendingBadges - finishedBadges

        int remainBadgeCount = this.pendingBadges.size()

        steps.echo("${remainBadgeCount} badges remain")
    }

    String getWorkspaceDir() {
        assert steps.env.BUILDGRAPH_WORKSPACE_DIR

        String nodeWorkspaceRoot = steps.env.BUILDGRAPH_WORKSPACE_DIR

        String branch = this.config.branchName

        // Multiple executors
        String executor = steps.env.EXECUTOR_NUMBER

        String workspaceDir = "${nodeWorkspaceRoot}/${branch}_${executor}"

        return workspaceDir
    }

    void startGroup(Map group) {
        // override environment
        List envList = []

        if (steps.env.P4_CLIENT) {
            String p4Client = steps.env.P4_CLIENT

            steps.echo "overriding p4 client with ${p4Client}"

            envList.add("uebp_CLIENT=${p4Client}")
        }

        if (config.changelist != PipelineConfiguration.defaultChangelist) {
            String changelist = config.changelist
            envList.add("uebp_CL=${changelist}")
        }

        List nodes = group['Nodes']

        steps.withEnv(envList) {
            nodes.each { node ->
                runNode(node)
            }
        }

        updateBadgeStatus()
    }

    void start() {
        steps.stage('Generate') {
            steps.node(primaryLabel) {
                steps.ws(workspaceDir) {
                    initialWorkspace()

                    runExport()

                    this.finishedNodes = []
                    this.failedNodes = []

                    initialBadgeStatus()
                }
            }
        }

        runPipeline()

        cleanup()

        int failedNodeCount = this.failedNodes.size()
        if (failedNodeCount > 0) {
            steps.error("Pipeline finished with ${failedNodeCount} failed nodes")
        }
    }

    protected void cleanup() {
        if (steps.env.BUILDGRAPH_SHARED_DIR) {
            steps.stage('Cleanup') {
                steps.node(primaryLabel) {

                    // We really don't care about the delete result
                    steps.bat(
                        returnStatus: true,
                        script: "rmdir /s /q \"${sharedDir}\""
                    )
                }
            }
        }
    }

    protected String getSharedDir() {
        assert steps.env.BUILDGRAPH_SHARED_DIR

        String sharedDirRoot = steps.env.BUILDGRAPH_SHARED_DIR

        String folderName = this.config.changelist
        String jobName = steps.env.JOB_NAME

        String sharedFolder = "${jobName}/${folderName}"

        return "${sharedDirRoot}/${sharedFolder}"
    }

    protected void runExport() {
        String tmpFile = steps.pwd(tmp: true)
        tmpFile = "${tmpFile}/Graph_Export.json"

        steps.bat "${runner} -Export=\"${tmpFile}\""

        Map graph = steps.readJSON(file: tmpFile)

        if (this.config.preview) {
            steps.echo(graph.toString())
        }

        this.graph = graph
    }

    protected String getRunner() {
        List params = []

        List options = this.config.graphOptions.collect { entry ->
            "-set:${entry.key}=\"${entry.value}\""
        }

        params += options

        // Allows nodes to connect to each other
        if (steps.env.BUILDGRAPH_SHARED_DIR) {
            params.add("-SharedStorageDir=\"${sharedDir}\"")

            params.add('-WriteToSharedStorage')
        }

        // Allows add custom param to runner
        if (this.config.graphParam) {
            params.add(this.config.graphParam)
        }

        String executable = this.config.runner
        String target = this.config.graphTarget
        String script = this.config.scriptPath
        String switches = params.join(' ')

        return "${executable} -Script=\"${script}\" ${switches} -Target=\"${target}\""
    }

    protected void runNode(Map nodeObj) {
        String nodeName = nodeObj['Name']
        List nodeDeps = nodeObj['DependsOn'].tokenize(';')
        int nodeDepCount = nodeDeps.size()

        try {
            steps.stage(nodeName) {
                if (nodeDepCount > 0 && this.parallel) {
                    steps.echo "${nodeName} node has ${nodeDepCount} dependencies, wait for them to finish"

                    steps.waitUntil {
                        nodeDeps.every { dep ->
                            this.finishedNodes.contains(dep)
                        }
                    }
                }

                if (this.config.preview) {
                    Utils.markStageSkippedForConditional(nodeName)
                } else {
                    boolean alreadyFailed = nodeDeps.any { dep ->
                        this.failedNodes.contains(dep)
                    }

                    if (alreadyFailed) {
                        steps.echo "The upstream dependencies of ${nodeName} are already failed, skipping..."
                        Utils.markStageSkippedForConditional(nodeName)
                        this.failedNodes.add(nodeName)
                    } else {
                        // TODO: Support Linux
                        steps.bat "${runner} -SingleNode=\"${nodeName}\""
                    }
                }
            }
        } catch (e) {
            this.failedNodes.add(nodeName)
        } finally {
            // Always add to the finished node list
            this.finishedNodes.add(nodeName)
        }
    }

    protected List postBadgeStatus(List badges, boolean initial = false) {
        List updatedBadges = []

        if (this.config.preview) {
            return updatedBadges
        }

        if (this.config.nobadge) {
            return updatedBadges
        }

        if (steps.env.BUILDGRAPH_METADATA_SERVER) {
            String metaServerUrl = steps.env.BUILDGRAPH_METADATA_SERVER

            List exeList = steps.findFiles(glob: 'Engine/Source/Programs/UnrealGameSync/**/PostBadgeStatus.exe')

            if (exeList.empty) {
                steps.warning('Cannot post badge status, the executable is missing')
                return updatedBadges
            }

            String exe = exeList.first().path

            badges.each { value ->
                String badgeName = value['Name']
                String badgeProject = value['Project']
                int badgeChange = value['Change']

                String status = null

                // Check status
                if (initial) {
                    status = 'Starting'
                } else {
                    List dependencies = value['DirectDependencies'].tokenize(';')

                    boolean isFinished = dependencies.every { dep ->
                        this.finishedNodes.contains(dep)
                    }

                    boolean isFailed = dependencies.any { dep ->
                        this.failedNodes.contains(dep)
                    }

                    if (isFailed) {
                        status = 'Failure'
                    } else if (isFinished) {
                        status = 'Success'
                    }
                }

                if (status != null) {
                    String buildUrl = steps.env.BUILD_URL

                    steps.bat(
                        returnStatus: true,
                        script: "${exe} -Name=${badgeName} -Change=${badgeChange} -Project=\"${badgeProject}\" -RestUrl=\"${metaServerUrl}\" -Status=${status} -Url=\"${buildUrl}\""
                    )
                    updatedBadges.add(value)
                }
            }
        }

        return updatedBadges
    }
}

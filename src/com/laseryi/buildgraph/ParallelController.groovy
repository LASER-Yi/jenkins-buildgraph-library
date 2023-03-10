package com.laseryi.buildgraph

class ParallelController extends BaseController {

    ParallelController(PipelineConfiguration config, Script steps) {
        super(config, steps)
        this.parallel = true
    }

    void blockGroup(Map group) {
        List nodes = group['Nodes']

        // Check RunEarly flag
        boolean shouldRunEarly = nodes.any { it['RunEarly'] == true }

        if (shouldRunEarly) {
            steps.echo 'RunEarly is enabled for this group, trying to dispatch the build as early as possible'
        }

        // build the deps graph of this agent
        List resolvedDeps = []
        List requiredDeps = []
        nodes.each { nodeObj ->
            resolvedDeps.add(nodeObj['Name'])
            List nodeDeps = nodeObj['DependsOn'].tokenize(';')

            // If RunEarly flag is enabled, find the node with RunEarly flag and add it to the required dependencies
            // Dispatch the group if all RunEarly nodes are satisfied

            // Noted that this behavior might create a deadlock when running on a instance with few executors 
            // Since we are taking up a executor but still wait for another tasks to be finished
            if (shouldRunEarly) {
                if (nodeObj['RunEarly'] == true) {
                    requiredDeps += nodeDeps
                }
            } else {
                // Otherwise, only dispatch this group when dependencies of ALL nodes are satisfied
                requiredDeps += nodeDeps
            }

        }

        // Excluded dependencies in the same group
        requiredDeps = requiredDeps - resolvedDeps

        int depCount = requiredDeps.size()

        if (depCount > 0) {
            steps.echo "Group has ${depCount} dependencies, wait for them to finish"

            steps.waitUntil {
                requiredDeps.every { dep ->
                    this.finishedNodes.contains(dep)
                }
            }
        }
    }

    String computeNodeLabel(Map group) {
        List agentTypes = group['Agent Types']

        if (agentTypes.empty) {
            return primaryLabel
        }

        String requirement = agentTypes.join(' || ')
        String nodeLabel = "${primaryLabel} && (${requirement})"

        List availableNodes = steps.nodesByLabel(nodeLabel)

        if (availableNodes.empty) {
            steps.warning('The agent types defined by this build graph will not allocate any Jenkins node, fallback to default agent type')
            return primaryLabel
        }

        return nodeLabel
    }

    void runParallelGroup(Map group) {
        String groupName = group['Name']

        String nodeLabel = computeNodeLabel(group)

        steps.stage(groupName) {
            blockGroup(group)

            steps.echo "Try allocating node with label ${nodeLabel}"

            // Allocate a node
            steps.node(nodeLabel) {
                steps.ws(workspaceDir) {
                    setupWorkspace()

                    startGroup(group)
                }
            }
        }
    }

    void runPipeline() {
        // Generate agent map
        List groups = this.graph['Groups']

        Map parallelAgents = [:]

        groups.each { group ->
            String groupName = group['Name']

            parallelAgents[groupName] = {
                runParallelGroup(group)
            }
        }

        parallelAgents.failFast = false

        steps.parallel(parallelAgents)
    }

}

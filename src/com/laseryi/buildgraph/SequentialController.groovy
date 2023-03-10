package com.laseryi.buildgraph

class SequentialController extends BaseController {

    SequentialController(PipelineConfiguration config, Script steps) {
        super(config, steps)
        this.parallel = false
    }

    void runPipeline() {
        // Allocate a node and run all stages

        steps.node(primaryLabel) {
            steps.ws(workspaceDir) {
                setupWorkspace()

                List groups = this.graph['Groups']

                groups.each { group ->
                    startGroup(group)
                }
            }
        }
    }
}

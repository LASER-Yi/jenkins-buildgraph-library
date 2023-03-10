package com.laseryi.buildgraph

class PipelineConfiguration implements Serializable {

    static String defaultChangelist = 'now'

    String scriptPath

    Object scm
    String branch
    String branchName
    String changelist

    String runner

    String graphParam
    String graphTarget
    Map graphOptions

    boolean preview
    boolean nocheckout
    boolean nobadge

    public PipelineConfiguration(String path) {
        this.scriptPath = path
    }

    void readFromParams(Map params) {
        assert params.containsKey('scm')
        assert params.containsKey('branch')

        this.graphTarget = 'All'
        if (params.containsKey('target')) {
            this.graphTarget = params.target
        }

        this.graphOptions = [:]
        if (params.containsKey('options')) {
            Closure closure = params.options
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure.delegate = this.graphOptions
            closure()
        }

        this.runner = 'Engine/Build/BatchFiles/RunUAT.bat BuildGraph'
        if (params.containsKey('executable')) {
            this.runner = params.executable
        }

        this.graphParam = null
        if (params.containsKey('param')) {
            this.graphParam = params.param
        }

        this.preview = false
        if (params.containsKey('preview')) {
            this.preview = (params.preview == true)
        }

        this.nocheckout = false
        if (params.containsKey('nocheckout')) {
            this.nocheckout = (params.nocheckout == true)
        }

        this.nobadge = false
        if (params.containsKey('nobadge')) {
            this.nobadge = (params.nobadge == true)
        }

        this.branch = params.branch
        this.branchName = this.branch.replace('/', '').replace('\\', '')

        this.scm = params.scm
        this.changelist = defaultChangelist
    }

}

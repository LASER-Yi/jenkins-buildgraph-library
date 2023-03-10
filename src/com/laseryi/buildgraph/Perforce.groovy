package com.laseryi.buildgraph

class Perforce {

    static Object scm(String stream, String changelist, String credential, String executor) {
        return [
            $class: 'PerforceScm',
            credential: credential,
            populate: defaultPopulate(changelist),
            workspace: workspace(stream, executor)
        ]
    }

    static Object scm(String credential) {
        return { stream, cl, executor ->
            return Perforce.scm(stream, cl, credential, executor)
        }
    }

    static Object scm(String credential, Closure custom) {
        return { stream, cl, executor ->
            Object client = Perforce.scm(stream, cl, credential, executor)
            custom(client)

            return client
        }
    }

    static Object defaultPopulate(String changelist) {
        return [
            $class: 'AutoCleanImpl',
            delete: false,
            parallel: [
                enable: true,
                minbytes: '1024',
                minfiles: '16',
                threads: '8'
            ],
            pin: changelist,
        ]
    }

    static Object workspace(String stream, String executor) {
        String streamName = stream.replace('/', '').replace('-', '_')

        assert executor.integer

        /* groovylint-disable-next-line GStringExpressionWithinString */
        String format = 'jenkins-${NODE_NAME}-' + "${executor}-${streamName}" + '-buildgraph'

        return [
            $class: 'StreamWorkspaceImpl',
            format: format,
            pinHost: false,
            streamName: stream
        ]
    }

    static Object codeFilter() {
        return [
            $class: 'FilterPatternListImpl',
            caseSensitive: false,
            patternText: '^.*\\.(c|cc|cpp|m|mm|rc|cs|csproj|h|hpp|inl|usf|ush|uproject|uplugin|sln)$'
        ]
    }

    static Object assetFilter() {
        return [
            $class: 'FilterPatternListImpl',
            caseSensitive: false,
            patternText: '^.*\\.(uasset|umap)$'
        ]
    }

    // simply connect asset and code filter is not working, use this if you want to build CI
    static Object unrealFilter() {
        return [
            $class: 'FilterPatternListImpl',
            caseSensitive: false,
            patternText: '^.*\\.(c|cc|cpp|m|mm|rc|cs|csproj|h|hpp|inl|usf|ush|uproject|uplugin|sln|uasset|umap)$'
        ]
    }
}

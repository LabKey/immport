function subjectfinder(studyData, loaded_studies)
{
// TODO -- pass in as configuration
    var studyfinderMaskId = 'studyfinderOuterDIV';
    var studyfinderAppId = 'studyfinderAppDIV';

//
// study detail pop-up window
//   (TODO angularify)
//

    var detailWindows = {};
    var detailShowing = null;
    var timerDeferShow = null;
    var vp = null;

    function showPopup(targetId, dim, member)
    {
        hidePopup();

        var target;
        if (targetId)
            target = Ext4.get(targetId);
        if (targetId && !target)
            console.log("element not found: " + targetId);
        var detailWindow = detailWindows[member];
        if (!detailWindow)
        {
            detailWindow = Ext4.create('Ext.window.Window', {
                width: 800,
                height: 600,
                resizable: true,
                layout: 'fit',
                baseCls: 'study-detail',
                bodyCls: 'study-detail',
                autoScroll: true,
                loader: {
                    autoLoad: true,
                    url: 'immport-studyDetail.view?_frame=none&study=' + member
                }
            });
//        detailWindows[member] = detailWindow;
        }
        var viewScroll = Ext4.getBody().getScroll();
        var viewSize = Ext4.getBody().getViewSize();
        var region = [viewScroll.left, viewScroll.top, viewScroll.left + viewSize.width, viewScroll.top + viewSize.height];
        var proposedXY;
        if (target)
        {
            var targetXY = target.getXY();
            proposedXY = [targetXY[0] + target.getWidth() - 100, targetXY[1]];
        }
        else
        {
            proposedXY = [region[0] + viewSize.width / 2 - 400, region[1] + viewSize.height / 2 - 300];
        }
        proposedXY[1] = Math.max(region[1], Math.min(region[3] - 400, proposedXY[1]));
        detailWindow.setPosition(proposedXY);
        detailShowing = detailWindow;
        detailShowing.show();
    }


    function hidePopup()
    {
        if (timerDeferShow)
        {
            clearTimeout(timerDeferShow);
            timerDeferShow = null;
        }
        if (detailShowing)
        {
            detailShowing.hide();
            detailShowing.destroy();
            detailShowing = null;
        }
    }


//
// angular scope prototype
//

    var studyfinderScope = function ()
    {
        this.filterChoice = {show: false};
    };
    studyfinderScope.prototype =
    {
        cube: null,
        mdx: null,
        searchTerms: '',
        searchMessage: '',
        studySubset: "ImmuneSpace",
        formatNumber: Ext4.util.Format.numberRenderer('0,000'),
        downArrow: LABKEY.contextPath + "/_images/arrow_down.png",
        rightArrow: LABKEY.contextPath + "/_images/arrow_right.png",
        activeTab: "Studies",
        filterChoice: null,

        fnTRUE: function (a)
        {
            return true;
        },
        fnFALSE: function (b)
        {
            return false;
        },

        countForStudy: function (study)
        {
            var uniqueName = study.memberName || study.uniqueName;
            var studyMember = dataspace.dimensions.Study.memberMap[uniqueName];
            return studyMember ? studyMember.count : 0;
        },

        anyVisibleStudies: function ()
        {
            var members = dataspace.dimensions.Study.members;
            for (var m = 0; m < members.length; m++)
                if (members[m].count)
                    return true;
            return false;
        },

        hasFilters: function ()
        {
            for (var d in dataspace.dimensions)
            {
                if (!dataspace.dimensions.hasOwnProperty(d))
                    continue;
                if (d == "Study")
                    continue;
                var filterMembers = dataspace.dimensions[d].filters;
                if (filterMembers && filterMembers.length > 0)
                    return true;
            }
            return false;
        },

        dimensionHasFilter: function (dim)
        {
            return (dim.filters && dim.filters.length) ? true : false;
        },

        displayFilterChoice: function (dimName, $event, $scope)
        {
            var dim = dataspace.dimensions[dimName];
            if (!dim)
                return;
            var xy = Ext4.fly($event.target).getXY();
            $scope.filterChoice =
            {
                show: true,
                dimName: dimName,
                x: xy[0],
                y: xy[1],
                options: dim.filterOptions
            };
            if ($event.stopPropagation)
                $event.stopPropagation();
        },

        setFilterType: function (dimName, type, $scope)
        {
            $scope.filterChoice.show = false;
            var dim = dataspace.dimensions[dimName];
            if (!dim)
                return;
            if (dim.filterType === type)
                return;
            for (var f = 0; f < dim.filterOptions.length; f++)
            {
                if (dim.filterOptions[f].type == type)
                {
                    dim.filterType = type;
                    dim.filterCaption = dim.filterOptions[f].caption;
                    this.updateCountsAsync();
                    return;
                }
            }
        },

        selectMember: function (dimName, member, $event)
        {
            var shiftClick = $event && ($event.ctrlKey || $event.altKey || $event.metaKey);
            this._selectMember(dimName, member, $event, shiftClick);
        },

        toggleMember: function (dimName, member, $event)
        {
            this._selectMember(dimName, member, $event, true);
        },

        _selectMember: function (dimName, member, $event, shiftClick)
        {
            var dim = dataspace.dimensions[dimName];
            var filterMembers = dim.filters;
            var m;

            if (!member)
            {
                if (0 == filterMembers.length)  // no change
                    return;
                this._clearFilter(dimName);
            }
            else if (!shiftClick)
            {
                this._clearFilter(dimName);
                dim.filters = [member];
                member.selected = true;
            }
            else
            {
                var index = -1;
                for (m = 0; m < filterMembers.length; m++)
                {
                    if (member.uniqueName == filterMembers[m].uniqueName)
                        index = m;
                }
                if (index == -1) // unselected -> selected
                {
                    filterMembers.push(member);
                    member.selected = true;
                }
                else // selected --> unselected
                {
                    filterMembers.splice(index, 1);
                    member.selected = false;
                }
            }
            this.updateCountsAsync();
            if ($event.stopPropagation)
                $event.stopPropagation();
        },


        clearAllFilters: function ()
        {
            for (var d in dataspace.dimensions)
            {
                if (!dataspace.dimensions.hasOwnProperty(d))
                    continue;
                if (d == "Study")
                    continue;
                this._clearFilter(d);
            }
            this.updateCountsAsync();
        },

        _clearFilter: function (dimName)
        {
            var dim = dataspace.dimensions[dimName];
            var filterMembers = dim.filters;
            for (var m = 0; m < filterMembers.length; m++)
                filterMembers[m].selected = false;
            dim.filters = [];
        },


        removeFilterMember: function (dim, member)
        {
            if (!dim || 0 == dim.filters.length) //  0 == dataspace.filters[dim.name].length)
                return;
            var filterMembers = dim.filters; // dataspace.filters[dim.name];
            var index = -1;
            for (var i = 0; i < filterMembers.length; i++)
            {
                if (member.uniqueName == filterMembers[i].uniqueName)
                    index = i;
            }
            if (index == -1)
                return;
            filterMembers[index].selected = false;
            filterMembers.splice(index, 1);
            this.updateCountsAsync();
        },


        initCubeMetaData: function ()
        {
            for (var name in dataspace.dimensions)
            {
                if (!dataspace.dimensions.hasOwnProperty(name))
                    continue;
                var dim = dataspace.dimensions[name];
                dim.hierarchy = this.cube.hierarchyMap[dim.hierarchyName];
                dim.level = dim.hierarchy.levelMap[dim.levelName];
                // using the cube objects directly makes angularjs debugging hard because of the pointers 'up' to level/hierarchy
                // so I'll copy them instead
                //        dim.members = dim.level.members;
                for (var m = 0; m < dim.level.members.length; m++)
                {
                    var src = dim.level.members[m];
                    if (src.name == "#notnull")
                        continue;
                    var member = {
                        name: src.name,
                        uniqueName: src.uniqueName,
                        selected: false,
                        level: src.level.uniqueName,
                        count: 0,
                        percent: 0,
                        filteredCount: -1,
                        selectedCount: -1
                    };
                    dim.members.push(member);
                    dim.memberMap[member.uniqueName] = member;
                }
            }
        },


        //filterByLevel: "[Study].[Name]",
        filterByLevel : "[Subject].[Subject]",


        updateCountsAsync: function ()
        {
            var intersectFilters = [];
            var d, i, dim;
            for (d in dataspace.dimensions)
            {
                if (!dataspace.dimensions.hasOwnProperty(d))
                    continue;
                dim = dataspace.dimensions[d];
                var filterMembers = dim.filters;
                if (d == 'Study')
                {
                    if (!filterMembers || filterMembers.length == dim.members.length)
                        continue;
                    if (filterMembers.length == 0)
                    {
                        // in the case of study filter, this means no matches, rather than no filter!
                        this.updateCountsZero();
                        return;
                    }
                    var uniqueNames = filterMembers.map(function(m){return m.uniqueName;});
                    if (this.filterByLevel != "[Study].[Name]")
                        intersectFilters.push({
                            level: this.filterByLevel,
                            membersQuery: {level: "[Study].[Name]", members: uniqueNames}
                        });
                    else
                        intersectFilters.push({level: "[Study].[Name]", members: uniqueNames});
                }
                else
                {
                    if (!filterMembers || filterMembers.length == 0)
                        continue;
                    if (dim.filterType === "OR")
                    {
                        var names = [];
                        filterMembers.forEach(function (m)
                        {
                            names.push(m.uniqueName)
                        });
                        intersectFilters.push({
                            level: this.filterByLevel,
                            membersQuery: {level: filterMembers[0].level, members: names}
                        });
                    }
                    else
                    {
                        for (i = 0; i < filterMembers.length; i++)
                        {
                            var filterMember = filterMembers[i];
                            intersectFilters.push({
                                level: this.filterByLevel,
                                membersQuery: {level: filterMember.level, members: [filterMember.uniqueName]}
                            });
                        }
                    }
                }
            }

            var filters = intersectFilters;
            if (intersectFilters.length && this.filterByLevel != "[Subject].[Subject]")
            {
                filters = [{
                    level: "[Subject].[Subject]",
                    membersQuery: {operator: "INTERSECT", arguments: intersectFilters}
                }]
            }

            var onRows = {operator: "UNION", arguments: []};
            for (d in dataspace.dimensions)
            {
                if (!dataspace.dimensions.hasOwnProperty(d))
                    continue;
                dim = dataspace.dimensions[d];
                if (dim.name == "Subject")
                    onRows.arguments.push({level: dim.hierarchy.levels[0].uniqueName});
                else if (dim.name == "Study" && this.filterByLevel == "[Study].[Name]")
                    continue;
                else
                    onRows.arguments.push({level: dim.level.uniqueName});
            }

            var config =
            {
                "sql": true,
                configId: 'ImmPort:/StudyCube',
                schemaName: 'ImmPort',
                name: 'StudyCube',
                success: function (cellset, mdx, config)
                {
                    // use angular timeout() for its implicit $scope.$apply()
                    //                config.scope.timeout(function(){config.scope.updateCounts(config.dim, cellset);},1);
                    config.scope.timeout(function ()
                    {
                        config.scope.updateCountsUnion(cellset);
                    }, 1);
                },
                scope: this,

                // query
                onRows: onRows,
                countFilter: filters,
                countDistinctLevel: '[Subject].[Subject]'
            };
            this.mdx.query(config);


            //if (this.filterByLevel == "[Study].[Name]")
            //{
            //    var configStudy =
            //    {
            //        "sql": true,
            //        "configId": "ImmPort:/StudyCube",
            //        "schemaName": "ImmPort",
            //        "name": "StudyCube",
            //        success: function (cellset, mdx, config)
            //        {
            //            // use angular timeout() for its implicit $scope.$apply()
            //            //                config.scope.timeout(function(){config.scope.updateCounts(config.dim, cellset);},1);
            //            config.scope.timeout(function ()
            //            {
            //                config.scope.updateCounts(dataspace.dimensions.Study, cellset);
            //            }, 1);
            //        },
            //        scope: this,
            //
            //        "onRows": {"level": "[Study].[Name]"},
            //        "countFilter": intersectFilters,
            //        countDistinctLevel: '[Study].[Name]'
            //    };
            //    this.mdx.query(configStudy);
            //}
        },


        updateCountsZero: function ()
        {
            for (d in dataspace.dimensions)
            {
                if (!dataspace.dimensions.hasOwnProperty(d))
                    continue;
                var dim = dataspace.dimensions[d];
                dim.summaryCount = 0;
                for (var m = 0; m < dim.members.length; m++)
                {
                    dim.members[m].count = 0;
                    dim.members[m].percent = 0;
                }
                dim.summaryCount = 0;
            }

            this.saveFilterState();
            this.updateContainerFilter();
            this.doneRendering();
        },


        updateCounts: function (dim, cellset)
        {
            var member, m;
            var memberMap = dim.memberMap;
            var positions = cellset.axes[1].positions;
            var max = 0;
            dim.summaryCount = 0;
            for (m = 0; m < dim.members.length; m++)
            {
                dim.members[m].count = 0;
            }
            for (var i = 0; i < positions.length; i++)
            {
                var uniqueName = positions[i][0].uniqueName;
                var count = cellset.cells[i][0].value || 0;
                member = memberMap[uniqueName];
                member.count = count;
                if (count > max)
                    max = count;
                dim.summaryCount += 1;
            }
            for (m = 0; m < dim.members.length; m++)
            {
                member = dim.members[m];
                dim.members[m].percent = max == 0 ? 0 : (100.0 * member.count) / max;
            }

            this.saveFilterState();
            this.updateContainerFilter();
            this.doneRendering();
        },


        updateCountsUnion: function (cellset)
        {
            var dim, member, d, m;
            // map from hierarchyName to dataspace dimension
            var map = {};

            // clear old counts (to be safe)
            for (d in dataspace.dimensions)
            {
                if (!dataspace.dimensions.hasOwnProperty(d))
                    continue;
                dim = dataspace.dimensions[d];
                map[dim.hierarchy.uniqueName] = dim;
                dim.summaryCount = 0;
                dim.allMemberCount = 0;
                for (m = 0; m < dim.members.length; m++)
                {
                    member = dim.members[m];
                    member.count = 0;
                    member.percent = 0;
                }
            }

            var positions = cellset.axes[1].positions;
            var max = 0;
            for (var i = 0; i < positions.length; i++)
            {
                var resultMember = positions[i][0];
                var hierarchyName = resultMember.level.hierarchy.uniqueName;
                dim = map[hierarchyName];
                var count = cellset.cells[i][0].value;
                member = dim.memberMap[resultMember.uniqueName];
                if (!member)
                {
                    // might be an all member
                    if (dim.allMemberName == resultMember.uniqueName)
                        dim.allMemberCount = count;
                    else (-1 == resultMember.uniqueName.indexOf("#") && "(All)" != resultMember.name)
                    console.log("member not found: " + resultMember.uniqueName);
                }
                else
                {
                    member.count = count;
                    if (count)
                        dim.summaryCount += 1;
                    if (count > max)
                        max = count;
                }
            }
            for (d in dataspace.dimensions)
            {
                dim = dataspace.dimensions[d];
                map[dim.hierarchy.uniqueName] = dim;
                for (m = 0; m < dim.members.length; m++)
                {
                    member = dim.members[m];
                    member.percent = max == 0 ? 0 : (100.0 * member.count) / max;
                }
            }
            this.doneRendering();
        },

        doneRendering: function ()
        {
            if (loadMask)
            {
                Ext4.get(studyfinderAppId).removeCls("x-hidden");
                loadMask.hide();
                loadMask = null;
                LABKEY.help.Tour.autoShow('immport.studyfinder');
            }

            LABKEY.Utils.signalWebDriverTest('studyFinderCountsUpdated');
        },

        clearStudyFilter: function ()
        {
            this.setStudyFilter(this.getStudySubsetList());
        },


        getStudySubsetList: function ()
        {
            if (this.studySubset == "ImmuneSpace")
                return this.loaded_study_list;
            if (this.studySubset == "Recent")
                return this.recent_study_list;
            if (this.studySubset == "HipcFunded")
                return this.hipc_study_list;
            return Ext4.pluck(this.dimStudy.members, 'uniqueName');
        },


        setStudyFilter: function (studies)
        {
            var dim = this.dimStudy;
            this._clearFilter(dim.name);
            var filterMembers = dim.filters;

            for (var s = 0; s < studies.length; s++)
            {
                var uniqueName = studies[s];
                var member = dim.memberMap[uniqueName];
                if (!member)
                {
                    console.log("study not found: " + uniqueName);
                    continue;
                }
                filterMembers.push(member);
            }
            this.updateCountsAsync();
        },


        onStudySubsetChanged: function ()
        {
            // if there are search terms, just act as if the search terms have changed
            if (this.searchTerms)
            {
                this.onSearchTermsChanged();
            }
            else
            {
                this.clearStudyFilter();
            }
        },


        doSearchTermsChanged_promise: null,

        doSearchTermsChanged: function ()
        {
            if (this.doSearchTermsChanged_promise)
            {
                // UNDONE:cancel doesn't seem to really be supported for $http
                //this.http.cancel(this.doSearchTermsChanged_promise);
            }

            if (!this.searchTerms)
            {
                this.searchMessage = "";
                this.clearStudyFilter();
                return;
            }

            this.searchMessage = "searching...";

            var scope = this;
            var url = LABKEY.ActionURL.buildURL("search", "json", LABKEY.containerPath, {
                "category": "immport_study",
                "scope": "site",
                "q": this.searchTerms
            });
            var promise = this.http.get(url);
            this.doSearchTermsChanged_promise = promise;
            promise.success(function (data)
            {
                // NOOP if we're not current (poor man's cancel)
                if (promise != scope.doSearchTermsChanged_promise)
                    return;
                scope.doSearchTermsChanged_promise = null;
                var hits = data.hits;
                var searchStudies = [];
                var found = {};
                for (var h = 0; h < hits.length; h++)
                {
                    var id = hits[h].id;
                    var accession = id.substring(id.lastIndexOf(':') + 1);
                    if (found[accession])
                        continue;
                    found[accession] = true;
                    searchStudies.push("[Study].[" + accession + "]");
                }
                if (!searchStudies.length)
                {
                    scope.clearStudyFilter();
                    scope.searchMessage = 'no matches';
                }
                else
                {
                    scope.searchMessage = '';
                    // intersect with study subset list
                    var result = scope.intersect(searchStudies, scope.getStudySubsetList());
                    scope.setStudyFilter(result);
                }
            });
        },

        intersect: function (a, b)
        {
            var o = {}, ret = [], i;
            for (i = 0; i < a.length; i++)
                o[a[i]] = a[i];
            for (i = 0; i < b.length; i++)
                if (o[b[i]])
                    ret.push(b[i]);
            return ret;
        },

        onSearchTermsChanged_promise: null,

        onSearchTermsChanged: function ()
        {
            if (null != this.onSearchTermsChanged_promise)
                this.timeout.cancel(this.onSearchTermsChanged_promise);
            var scope = this;
            this.onSearchTermsChanged_promise = this.timeout(function ()
            {
                scope.doSearchTermsChanged();
            }, 500);
        },

// save just the filtered uniqueNames for each dimension
        saveFilterState: function ()
        {
            if (!this.localStorageService.isSupported)
                return;

            for (var d in dataspace.dimensions)
            {
                if (!dataspace.dimensions.hasOwnProperty(d))
                    continue;
                if (d == "Study" && this.filterByLevel == "[Study].[Name]")
                    continue;

                var dim = dataspace.dimensions[d];
                var filterMembers = dim.filters;
                if (!filterMembers || filterMembers.length == 0)
                {
                    this.localStorageService.remove(dim.name);
                }
                else
                {
                    var filteredNames = [];
                    for (var i = 0; i < filterMembers.length; i++)
                    {
                        filteredNames.push(filterMembers[i].uniqueName);
                    }
                    this.localStorageService.set(dim.name, filteredNames);
                }
            }
        },

// load just the filtered uniqueNames for each dimension
        loadFilterState: function ()
        {
            if (!this.localStorageService.isSupported)
                return;

            for (var d in dataspace.dimensions)
            {
                if (!dataspace.dimensions.hasOwnProperty(d))
                    continue;
                if (d == "Study" && this.filterByLevel == "[Study].[Name]")
                    continue;

                var dim = dataspace.dimensions[d];
                var filteredNames = this.localStorageService.get(dim.name);
                if (filteredNames && filteredNames.length)
                {
                    for (var i = 0; i < filteredNames.length; i++)
                    {
                        var filteredName = filteredNames[i];
                        var member = dim.memberMap[filteredName];
                        if (member)
                        {
                            member.selected = true;
                            dim.filters.push(member);
                        }
                    }
                }
            }
        },

        updateContainerFilter: function ()
        {
            // Collect the container ids of the loaded studies
            var dim = dataspace.dimensions.Study;
            var containers = [];
            for (var name in loaded_studies)
            {
                if (!loaded_studies.hasOwnProperty(name))
                    continue;
                var study = loaded_studies[name];
                var count = this.countForStudy(study);
                if (count)
                    containers.push(study.containerId);
            }

            if (containers.length == 0 || containers.length == this.loaded_study_list.length)
            {
                // Delete the shared container filter if all loaded_studies are selected
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('study-shared', 'sharedStudyContainerFilter.api'),
                    method: 'DELETE'
                });
            }
            else
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('study-shared', 'sharedStudyContainerFilter.api'),
                    method: 'POST',
                    jsonData: {containers: containers}
                });
            }
        },

        showStudyPopup: function (study_accession)
        {
            showPopup(null, 'study', study_accession);
        }

    };


    var studyfinderApp = angular.module('studyfinderApp', ['LocalStorageModule'])
    .config(function (localStorageServiceProvider)
    {
        localStorageServiceProvider.setPrefix("LABKEY.immport.studyfinder");
    })
    .controller('studyfinder', function ($scope, $timeout, $http, localStorageService)
    {
        window.debug_scope = $scope;
        Ext4.apply($scope, new studyfinderScope());
        $scope.timeout = $timeout;
        $scope.http = $http;
        $scope.localStorageService = localStorageService;

        localStorageService.bind($scope, 'searchTerms');

        var studies = [];
        var loaded_study_list = [];
        var recent_study_list = [];
        var hipc_study_list = [];
        for (var i = 0; i < studyData.length; i++)
        {
            var name = studyData[i][0];
            var s =
            {
                'memberName': "[Study].[" + name + "]",
                'study_accession': name,
                'id': studyData[i][1], 'title': studyData[i][2], 'pi': studyData[i][3],
                'hipc_funded': false,
                'loaded': false,
                'url': null,
                'containerId': null
            };
            if (loaded_studies[name])
            {
                s.loaded = true;
                s.hipc_funded = loaded_studies[name].hipc_funded;
                s.highlight = loaded_studies[name].highlight;
                s.url = loaded_studies[name].url;
                s.containerId = loaded_studies[name].containerId;
                loaded_study_list.push(s.memberName);
                if (s.highlight)
                    recent_study_list.push(s.memberName);
                if (s.hipc_funded)
                    hipc_study_list.push(s.memberName);
            }
            studies.push(s);
        }

        $scope.dataspace = dataspace;
        $scope.studies = studies;
        $scope.loaded_study_list = loaded_study_list;
        $scope.recent_study_list = recent_study_list;
        $scope.hipc_study_list = hipc_study_list;
//    $scope.filterMembers = [];

        // shortcuts
        $scope.dimSubject = dataspace.dimensions.Subject;
        $scope.dimStudy = dataspace.dimensions.Study;
        $scope.dimCondition = dataspace.dimensions.Condition;
        $scope.dimSpecies = dataspace.dimensions.Species;
        $scope.dimPrincipal = dataspace.dimensions.Principal;
        $scope.dimGender = dataspace.dimensions.Gender;
        $scope.dimRace = dataspace.dimensions.Race;
        $scope.dimAge = dataspace.dimensions.Age;
        $scope.dimTimepoint = dataspace.dimensions.Timepoint;
        $scope.dimAssay = dataspace.dimensions.Assay;
        $scope.dimType = dataspace.dimensions.Type;
        $scope.dimCategory = dataspace.dimensions.Category;

        $scope.cube = LABKEY.query.olap.CubeManager.getCube({
            configId: 'ImmPort:/StudyCube',
            schemaName: 'ImmPort',
            name: 'StudyCube',
            deferLoad: false
        });
        $scope.cube.onReady(function (m)
        {
            $scope.$apply(function ()
            {
                $scope.mdx = m;
                $scope.initCubeMetaData();
                $scope.loadFilterState();

                // init study list according to studySubset
                if (loaded_study_list.length == 0)
                    $scope.studySubset = "ImmPort";
                $scope.onStudySubsetChanged();
                // doShowAllStudiesChanged() has side-effect of calling updateCountsAsync()
                //$scope.updateCountsAsync();
            });
        });

    });


    var dataspace =
    {
        dimensions:
        {
            "Study":
            {
                name: 'Study', pluralName: 'Studies', hierarchyName: 'Study', levelName: 'Name', allMemberName: '[Study].[(All)]', popup: true,
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            },
            "Condition":
            {
                name: 'Condition', hierarchyName: 'Study.Conditions', levelName: 'Condition', allMemberName: '[Study.Conditions].[(All)]',
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            },
            "Assay":
            {
                name: 'Assay', hierarchyName: 'Assay', levelName: 'Assay', allMemberName: '[Assay].[(All)]',
                filterType: "AND", filterOptions: [{type: "OR", caption: "data for any of these"}, { type: "AND", caption: "data for all of these"}]
            },
            "Type":
            {
                name: 'Type', hierarchyName: 'Study.Type', levelName: 'Type', allMemberName: '[Study.Type].[(All)]',
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            },
            "Category":
            {
                caption: 'Research focus', name: 'Category', hierarchyName: 'Study.Category', levelName: 'Category', allMemberName: '[Study.Category].[(All)]',
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            },
            "Timepoint":
            {
                caption: 'Day of Study', name: 'Timepoint', hierarchyName: 'Timepoint.Timepoints', levelName: 'Timepoint', allMemberName: '[Timepoint.Timepoints].[(All)]',
                filterType: "AND", filterOptions: [{type: "OR", caption: "has data for any of"}, { type: "AND", caption: "has data for all of"}]
            },
            "Race":
            {
                name: 'Race', hierarchyName: 'Subject.Race', levelName: 'Race', allMemberName: '[Subject.Race].[(All)]',
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            },
            "Age":
            {
                name: 'Age', hierarchyName: 'Subject.Age', levelName: 'Age', allMemberName: '[Subject.Age].[(All)]',
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            },
            "Gender":
            {
                name: 'Gender', hierarchyName: 'Subject.Gender', levelName: 'Gender', allMemberName: '[Subject.Gender].[(All)]',
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            },
            "Species":
            {
                name: 'Species', pluralName: 'Species', hierarchyName: 'Subject.Species', levelName: 'Species', allMemberName: '[Subject.Species].[(All)]',
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            },
            "Principal":
            {
                name: 'Principal', pluralName: 'Species', hierarchyName: 'Study.Principal', levelName: 'Principal', allMemberName: '[Study.Principal].[(All)]',
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            },
            "Subject":
            {
                name: 'Subject', hierarchyName: 'Subject', levelName: 'Subject', allMemberName: '[Subject].[(All)]',
                filterType: "OR", filterOptions: [{type: "OR", caption: "is any of"}]
            }
        }
    };
    for (var p in dataspace.dimensions)
    {
        var dim = dataspace.dimensions[p];
        Ext4.apply(dim, {members: [], memberMap: {}, filters: [], summaryCount: 0, allMemberCount: 0});
        dim.pluralName = dim.pluralName || dim.name + 's';
        dim.filterType = dim.filterType || "OR";
        for (var f = 0; f < dim.filterOptions.length; f++)
        {
            if (dim.filterOptions[f].type == dim.filterType)
                dim.filterCaption = dim.filterOptions[f].caption;
        }
    }

    var loadMask = null;

    Ext4.onReady(function ()
    {
        loadMask = new Ext4.LoadMask(Ext4.get(studyfinderMaskId), {msg: "Loading study definitions..."});
        loadMask.show();
    });
}


// NOTE LABKEY.ext4.Util.resizeToViewport only accepts an ext component
function resizeToViewport(el, width, height, paddingX, paddingY, offsetX, offsetY)
{
    el = Ext4.get(el);
    if (!el)
        return;

    if (width < 0 && height < 0)
        return;

    var padding = [];
    if (offsetX == undefined || offsetX == null)
        offsetX = 35;
    if (offsetY == undefined || offsetY == null)
        offsetY = 35;

    if (paddingX !== undefined && paddingX != null)
        padding.push(paddingX);
    else
    {

        var bp = Ext4.get('bodypanel');
        if (bp)
        {
            var t = Ext4.query('table.labkey-proj');
            if (t && t.length > 0)
            {
                t = Ext4.get(t[0]);
                padding.push((t.getWidth() - (bp.getWidth())) + offsetX);
            }
            else
                padding.push(offsetX);
        }
        else
            padding.push(offsetX);
    }
    if (paddingY !== undefined && paddingY != null)
        padding.push(paddingY);
    else
        padding.push(offsetY);

    var xy = el.getXY();
    var size = {
        width: Math.max(100, width - xy[0] - padding[0]),
        height: Math.max(100, height - xy[1] - padding[1])
    };

    if (width < 0)
        el.setHeight(size.height);
    else if (height < 0)
        el.setWidth(size.width);
    else
        el.setSize(size);
}


LABKEY.Study = LABKEY.Study || {};

LABKEY.Study.ParticipantGroup = new function()
{
    function defaultValue(value,def)
    {
        return undefined===value ? def : value;
    }

    return {

        /** return a list of saved participant groups
         * @param config An object that contains the following configuration parameters
         * @param {boolean} config.includePrivateGroups
         * @param {String[]} config.type indicate what types of data to return values can be "participantGroup", "participantCategory", "cohort"
         * @param {boolean} config.includeParticipantIds include the participant list in the response, this can greatly increase the size of the response data, default==false
         *              <b>in release 20.3 and early includeParticipantIds==true only works for cohorts</b>
         * @param {int} config.categoryId filter groups list to a particular category
         * @param {boolean} config.includeUnassigned if true then return a virtual "not in any group" or "not in any cohort" group for each category
         * @param {boolean} config.distinctCategories if true then private categories hide shared categories with the same label, default==true
         * @param {function} config.success
         * @returns {object} success:true, groups:[array of ParticipantGroup]}
         *
         * ParticipantGroup
         * <ul>
         *     <li>id: {int} unique identifier, value > 0 if this is a saved participant group
         *     <li>participantIds:[] array of {string} participantIds
         *     <li>participantSet: // do not use (bug)
         *     <li>label: {string}
         *     <li>description: {string}
         *     <li>filters: {string} application defined string used to record how this participant group was created/defined
         *     <li>type: {string} "participantGroup", "cohort", or "participantCategory"
         *     <li>category: {object} see ParticipantGroupCategory description
         * </ul>
         *
         * ParticipantGroupCategory
         * <ul>
         *     <li> rowId: unique identifier for the category
         *     <li> shared: {boolean} created for use by this user, or shared across users
         *     <li> label: {string}
         *     <li> type: {string}         "manual", "list", "query", or "cohort"
         *     <li> participantIds: [{string}] huh? bug?
         *     <li> filters: {string} bug?
         * </ul>
         */
        browseParticipantGroups : function(config) {
            var jsonData = {
                includePrivateGroups: defaultValue(config.includePrivateGroups, true),
                type: defaultValue(config.type, ['participantGroup']),
                includeParticipantIds: defaultValue(config.includeParticipantIds, false),
                categoryId: defaultValue(config.categoryId, -1),
                includeUnassigned: defaultValue(config.includeUnassigned, false),
                distinctCategories: defaultValue(config.distinctCategories, true)
            };
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('participant-group', 'browseParticipantGroups.api'),
                method: 'POST',
                jsonData: jsonData,
                scope: config.scope || window,
                success: config.success,
                failure: config.failure
            });
        },

        /** return a list of saved participant groups
         * @param config An object that contains the following configuration parameters
         * @param {int} config.groupId
         * @param {boolean} config.includeParticipantIds include the participant list in the response default==true
         * @param {function} config.success TODO
         * @returns same as #browseParticipantGroups except 'groups' array will return zero or one group.
         */
        getParticipantGroup : function(config)
        {
            if (!config.groupId)
                throw "You must specify a groupId!";
            var jsonData = {
                includePrivateGroups: true,
                type: defaultValue(config.type, ['participantGroup']),
                includeParticipantIds: defaultValue(config.includeParticipantIds, true),
                categoryId: -1,
                groupId: config.groupId,
                includeUnassigned: false
            };
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('participant-group', 'browseParticipantGroups.api'),
                method: 'POST',
                jsonData: jsonData,
                scope: config.scope || window,
                success: config.success,
                failure: config.failure,
            });
        },

        /** Create or re-save a participant group
         * TODO permissions shared/private?
         * @param config An object that contains the following configuration parameters
         * @param {int} config.groupId (server actually uses rowId)
         * @param {String} config.label
         * @param {String} config.description
         * @param {String} config.filters
         * @param {String []} config.participantIds
         * @param {int} config.categoryId
         * @param {String} config.categoryLabel
         * @param {String} config.categoryType
         * @param {int} config.categoryOwnerid
         * @param {function} config.success TODO
         */
        saveParticipantGroup : function(config)
        {
            var jsonData = {
                rowId: config.groupId || config.rowId,
                label: defaultValue(config.label, ''),
                description : defaultValue(config.description, ''),
                filters : defaultValue(config.filters, ''),
                participantIds : defaultValue(config.participantIds, []),
                categoryId : defaultValue(config.categoryId,''),
                categoryLabel : defaultValue(config.categoryLabel, ''),
                categoryType : defaultValue(config.categoryType, ''),
                categoryOwnerId : defaultValue(config.categoryOwnerId, -1)
            };
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('participant-group', 'saveParticipantGroup.api'),
                method: 'POST',
                jsonData: jsonData,
                scope: config.scope || window,
                success: config.success,
                failure: config.failure
            });
        },

        /** add participants to an existing group (NYI, but supported by UpdateParticipantGroup.api) */
        addParticipantsToGroup : function()
        {
            throw "not implemented yet"
        },

        /** remove participants from an existing group (NYI, but supported by UpdateParticipantGroup.api) */
        removeParticipantsFromGroup : function()
        {
            throw "not implemented yet"
        },

        /**
         * Delete a saved participant group
         * TODO permissions
         * @param config An object that contains the following configuration parameters
         * @param {int} config.rowId        // TODO why is this RowId instead of Id or groupId???
         * @param {function} config.success TODO
         */
        deleteParticipantGroup : function(config)
        {
            if (!(config.groupId || config.rowId))
                throw "You must specify a groupId!";
            var jsonData = {
                rowId: config.groupId || config.rowId
            };
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('participant-group', 'deleteParticipantGroup.api'),
                method: 'POST',
                jsonData: jsonData,
                scope: config.scope || window,
                success: config.success,
                failure: config.failure
            });
        },

        getSessionParticipantGroup : function(config)
        {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('participant-group', 'getSessionParticipantGroup.api'),
                method: 'GET',
                scope: config.scope || window,
                success: config.success,
                failure: config.failure
            });
        },


        /** Create or re-save a participant group
         * TODO permissions shared/private?
         * @param config An object that contains the following configuration parameters
         * @param {int} config.groupId specify the id of a saved participant group to use as the session participant group.
         *  This is not used with the other parameters (label, description, filters). Leave unspecified to use an unsaved session group.
         * @param {String} config.label
         * @param {String} config.description
         * @param {String} config.filters
         * @param {String []} config.participantIds
         * @param {function} config.success TODO
         */
        setSessionParticipantGroup : function(config)
        {
            var groupId = config.groupId || config.rowId || 0;
            var jsonData = groupId ? {rowId : groupId} :
            {
                rowId: groupId,
                label: defaultValue(config.label, ''),
                description : defaultValue(config.description, ''),
                filters : defaultValue(config.filters, ''),
                participantIds : defaultValue(config.participantIds, [])
            };
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('participant-group', 'sessionParticipantGroup.api'),
                method: 'POST',
                jsonData: jsonData,
                scope: config.scope || window,
                success: config.success,
                failure: config.failure
            });
        },

        /**
         * Clears the currently set session participant group (if any)
         * @param config An object that contains the following configuration parameters
         * @param {function} config.success The function to call when the function finishes successfully.
         */
        clearSessionParticipantGroup : function(config)
        {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('participant-group', 'sessionParticipantGroup.api'),
                method: 'DELETE',
                jsonData: {},
                scope: config.scope || window,
                success: config.success,
                failure: config.failure
            });
        }
    };
};

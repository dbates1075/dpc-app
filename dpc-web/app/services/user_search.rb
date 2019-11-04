# frozen_string_literal: true

class UserSearch
  ALLOWED_SCOPES = %i[all assigned_non_vendor assigned_vendor].freeze

  attr_reader :params, :initial_scope

  def initialize(params: {}, scope: :all)
    raise ArgumentError unless ALLOWED_SCOPES.include? scope

    @params = params
    @initial_scope = scope
  end

  def results
    @results ||= query
  end

  private

  def query
    scope = User.send(initial_scope)

    scope = apply_org_queries(scope)
    scope = apply_date_queries(scope)
    scope = apply_keyword_search(scope)

    scope
  end

  def apply_org_queries(scope)
    if params[:org_status] == 'unassigned'
      scope = scope.unassigned
    elsif params[:org_status] == 'assigned'
      scope = scope.assigned
    end

    if params[:requested_org].present?
      org = "%#{params[:requested_org].downcase}%"
      scope = scope.where('LOWER(users.requested_organization) LIKE :org', org: org)
    end

    if params[:requested_org_type].present?
      scope = scope.where(requested_organization_type: params[:requested_org_type])
    end

    scope
  end

  def apply_date_queries(scope)
    if params[:created_after].present?
      scope = scope.where('users.created_at > :created_after', created_after: params[:created_after])
    end

    if params[:created_before].present?
      scope = scope.where('users.created_at < :created_before', created_before: params[:created_before])
    end

    scope
  end

  def apply_keyword_search(scope)
    if params[:keyword].present?
      scope = scope.by_keyword(params[:keyword])
    end

    scope
  end
end